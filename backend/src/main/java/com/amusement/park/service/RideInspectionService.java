package com.amusement.park.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.Dict;
import com.amusement.park.entity.Ride;
import com.amusement.park.entity.RideInspection;
import com.amusement.park.repository.RideInspectionRepository;
import com.amusement.park.repository.RideRepository;

/**
 * 检修后安全复核与放行闭环。
 *
 * <p>① 设施进入检修自动生成本次复核批次；② 安全员逐项记录四项必检结果，
 * 全部合格才能提交；③ 提交人不能担任最终放行人；④ 负责人仅能对仍处于检修、
 * 复核内容未过期（快照与当前限制一致）的「待放行」批次确认恢复开放；
 * ⑤ 提交后设施限制/状态/复核项发生变化 → 原批次标记「已失效」（历史保留可追溯，
 * 不被新批次覆盖），设施仍在检修时自动补发新批次；⑥ 放行按批次幂等：同一放行人
 * 重复点击/刷新重试返回同一结果，两人同时放行只有一人成功，未成功者看到设施
 * 当前状态，开放状态不会被回滚。</p>
 *
 * <p>并发约定：全系统统一按 ride → inspection 的顺序加悲观锁，避免死锁。</p>
 */
@Service
public class RideInspectionService {

    private static final String ST_DRAFT = "填写中";
    private static final String ST_SUBMITTED = "待放行";
    private static final String ST_RELEASED = "已放行";
    private static final String ST_INVALID = "已失效";

    private final RideInspectionRepository inspectionRepository;
    private final RideRepository rideRepository;

    public RideInspectionService(RideInspectionRepository inspectionRepository, RideRepository rideRepository) {
        this.inspectionRepository = inspectionRepository;
        this.rideRepository = rideRepository;
    }

    public RideInspection get(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new BizException("复核批次不存在：" + id));
    }

    /** 批次历史（新→旧），含已失效批次，永久可追溯 */
    public List<RideInspection> history(Long rideId) {
        return inspectionRepository.findByRideIdOrderByIdDesc(rideId);
    }

    /** 当前进行中的批次（填写中/待放行），无则 null */
    public RideInspection active(Long rideId) {
        List<RideInspection> list = inspectionRepository
                .findByRideIdAndStatusInOrderByIdDesc(rideId, List.of(ST_DRAFT, ST_SUBMITTED));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 确保检修中的设施存在本次复核批次。设施进入检修时由 RideService 调用；
     * 前端「发起复核」也走这里——已有进行中批次则直接返回（幂等，不重复建批）。
     */
    @Transactional
    public RideInspection ensureDraft(Long rideId) {
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new BizException("设施不存在：" + rideId));
        if (!"检修".equals(ride.getStatus())) {
            throw new BizException("设施「" + ride.getRideName() + "」当前为「" + ride.getStatus()
                    + "」，仅检修中的设施需要安全复核批次");
        }
        RideInspection existing = active(rideId);
        if (existing != null) {
            return existing;
        }
        return newBatch(ride);
    }

    /**
     * 逐项记录四项必检结果。
     *
     * <p>「填写中」直接保存；「待放行」批次被修改 → 原批次标记「已失效」（历史保留），
     * 并以新值生成新的填写中批次；已放行/已失效的历史批次不可修改。</p>
     */
    @Transactional
    public RideInspection saveItems(Long id, RideInspection input) {
        RideInspection batch = lockBatch(id);
        if (ST_RELEASED.equals(batch.getStatus()) || ST_INVALID.equals(batch.getStatus())) {
            throw new BizException("批次已「" + batch.getStatus() + "」，历史结果不可修改");
        }
        if (ST_SUBMITTED.equals(batch.getStatus())) {
            invalidate(batch, "复核项在提交后被修改，原批次失效");
            RideInspection next = newBatch(getRide(batch.getRideId()));
            applyItems(next, input);
            return inspectionRepository.save(next);
        }
        applyItems(batch, input);
        return inspectionRepository.save(batch);
    }

    /**
     * 提交复核：四项必检全部合格才允许；记录提交人、提交时间与设施限制快照。
     * 同一提交人重复提交幂等返回（重复点击/刷新重试不产生新结果）。
     */
    @Transactional
    public RideInspection submit(Long id, String operator) {
        if (operator == null || operator.isBlank()) {
            throw new BizException("请填写安全员（提交人）姓名");
        }
        String op = operator.trim();
        RideInspection batch = lockBatch(id);
        if (ST_SUBMITTED.equals(batch.getStatus())) {
            if (op.equals(batch.getSubmittedBy())) {
                return batch;
            }
            throw new BizException("该批次已由「" + batch.getSubmittedBy() + "」提交，待负责人放行");
        }
        if (!ST_DRAFT.equals(batch.getStatus())) {
            throw new BizException("批次状态为「" + batch.getStatus() + "」，不能提交");
        }
        if (!allPassed(batch)) {
            throw new BizException("所有必检项（" + String.join("、", Dict.RIDE_CHECK_ITEMS) + "）合格后才能提交");
        }
        Ride ride = getRide(batch.getRideId());
        batch.setSnapshotMinHeightCm(ride.getMinHeightCm());
        batch.setSnapshotMaxAge(ride.getMaxAge());
        batch.setSubmittedBy(op);
        batch.setSubmittedAt(LocalDateTime.now());
        batch.setStatus(ST_SUBMITTED);
        return inspectionRepository.save(batch);
    }

    /**
     * 负责人确认恢复开放（最终放行）。
     *
     * <p>先锁设施行再锁批次行，两人同时放行只有一人生效；同一放行人重复调用
     * 幂等返回；其他放行人调用时提示已由谁放行并告知设施当前状态，绝不回滚
     * 开放状态。放行成功后设施转为「开放」，乘坐校验立即按最新限制生效。</p>
     */
    @Transactional
    public RideInspection release(Long id, String operator) {
        if (operator == null || operator.isBlank()) {
            throw new BizException("请填写负责人（放行人）姓名");
        }
        String op = operator.trim();
        Long rideId = get(id).getRideId();
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new BizException("设施不存在：" + rideId));
        RideInspection batch = lockBatch(id);

        if (ST_RELEASED.equals(batch.getStatus())) {
            if (op.equals(batch.getReleasedBy())) {
                return batch; // 重复点击/刷新重试：幂等成功，不重复执行
            }
            throw new BizException("该批次已由「" + batch.getReleasedBy() + "」放行，本次未重复执行；设施当前状态："
                    + ride.getStatus());
        }
        if (ST_INVALID.equals(batch.getStatus())) {
            throw new BizException("该批次已失效（" + batch.getInvalidateReason()
                    + "），请使用最新复核批次；设施当前状态：" + ride.getStatus());
        }
        if (!ST_SUBMITTED.equals(batch.getStatus())) {
            throw new BizException("批次尚未提交（当前「" + batch.getStatus() + "」），不能放行");
        }
        if (!"检修".equals(ride.getStatus())) {
            throw new BizException("设施当前为「" + ride.getStatus() + "」，仅仍处于检修的批次可确认恢复开放");
        }
        if (op.equals(batch.getSubmittedBy())) {
            throw new BizException("提交人「" + op + "」不能担任最终放行人，安全员与放行人须分离");
        }
        // 兜底：复核内容过期（主动失效之外的第二道防线）
        if (!Objects.equals(batch.getSnapshotMinHeightCm(), ride.getMinHeightCm())
                || !Objects.equals(batch.getSnapshotMaxAge(), ride.getMaxAge())) {
            invalidate(batch, "设施乘坐限制在提交后已变更，复核内容过期");
            newBatch(ride);
            throw new BizException("设施限制在提交后已变更，复核内容过期，批次已标记失效并生成新批次，请重新复核");
        }
        batch.setStatus(ST_RELEASED);
        batch.setReleasedBy(op);
        batch.setReleasedAt(LocalDateTime.now());
        inspectionRepository.save(batch);
        ride.setStatus("开放");
        rideRepository.save(ride);
        return batch;
    }

    /**
     * 设施限制/状态发生变化后由 RideService 调用：把「待放行」批次标记为「已失效」
     * （历史保留可追溯）；设施仍在检修时自动补发新的填写中批次，保证闭环可继续。
     */
    @Transactional
    public void invalidatePending(Long rideId, String reason) {
        List<RideInspection> pending = inspectionRepository
                .findByRideIdAndStatusInOrderByIdDesc(rideId, List.of(ST_SUBMITTED));
        for (RideInspection batch : pending) {
            invalidate(batch, reason);
        }
        if (!pending.isEmpty()) {
            Ride ride = rideRepository.findById(rideId).orElse(null);
            if (ride != null && "检修".equals(ride.getStatus()) && active(rideId) == null) {
                newBatch(ride);
            }
        }
    }

    // ---------------- 内部 ----------------

    private RideInspection lockBatch(Long id) {
        return inspectionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("复核批次不存在：" + id));
    }

    private Ride getRide(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new BizException("设施不存在：" + rideId));
    }

    private boolean allPassed(RideInspection b) {
        return Boolean.TRUE.equals(b.getBrakeOk())
                && Boolean.TRUE.equals(b.getRestraintOk())
                && Boolean.TRUE.equals(b.getEmergencyStopOk())
                && Boolean.TRUE.equals(b.getTrialRunOk());
    }

    private void applyItems(RideInspection b, RideInspection input) {
        b.setBrakeOk(input.getBrakeOk());
        b.setRestraintOk(input.getRestraintOk());
        b.setEmergencyStopOk(input.getEmergencyStopOk());
        b.setTrialRunOk(input.getTrialRunOk());
        b.setRemark(input.getRemark());
    }

    private void invalidate(RideInspection batch, String reason) {
        batch.setStatus(ST_INVALID);
        batch.setInvalidatedAt(LocalDateTime.now());
        batch.setInvalidateReason(reason);
        inspectionRepository.save(batch);
    }

    /** 新批次：先占位拿到自增 id，再回填可读批次号（INSP-设施编号-序号）；历史批次永不被覆盖 */
    private RideInspection newBatch(Ride ride) {
        RideInspection batch = new RideInspection();
        batch.setRideId(ride.getId());
        batch.setBatchNo("NEW-" + UUID.randomUUID());
        batch.setStatus(ST_DRAFT);
        batch.setCreatedAt(LocalDateTime.now());
        batch = inspectionRepository.saveAndFlush(batch);
        batch.setBatchNo("INSP-" + ride.getRideCode() + "-" + String.format("%04d", batch.getId()));
        return inspectionRepository.save(batch);
    }
}
