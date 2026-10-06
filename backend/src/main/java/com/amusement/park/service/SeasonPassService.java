package com.amusement.park.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.Dict;
import com.amusement.park.entity.Member;
import com.amusement.park.entity.SeasonPass;
import com.amusement.park.repository.MemberRepository;
import com.amusement.park.repository.SeasonPassRepository;

import jakarta.persistence.EntityManager;

/**
 * 年卡 / 季卡业务（与会员共享主键）。
 *
 * <p>办卡 / 续期日期区间校验：起止均必填，且 {@code validFrom <= validTo}。
 * 乘坐核验按闭区间（含首尾）判定，起止可以是同一天（当日卡）。</p>
 *
 * <p>并发约定：办卡与续期都对卡片行（共享主键）加悲观锁，保证重复点击/并发请求
 * 串行执行——办卡只有第一个请求成功，其余得到“已持卡，请续期”；共享主键唯一约束
 * 是第二道防线。续期在单事务内覆盖日期并提交，乘坐核验每次重新读库，成功续期
 * 不会被另一个失败请求回滚（失败整体回滚、成功整体提交）。</p>
 */
@Service
public class SeasonPassService {

    private final SeasonPassRepository seasonPassRepository;
    private final MemberRepository memberRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public SeasonPassService(SeasonPassRepository seasonPassRepository,
                             MemberRepository memberRepository,
                             EntityManager entityManager,
                             Clock clock) {
        this.seasonPassRepository = seasonPassRepository;
        this.memberRepository = memberRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public List<SeasonPass> list() {
        return seasonPassRepository.findAll();
    }

    public SeasonPass getByMember(Long memberId) {
        return seasonPassRepository.findById(memberId)
                .orElseThrow(() -> new BizException("未找到该会员的年卡/季卡：" + memberId));
    }

    @Transactional
    public SeasonPass issue(Long memberId, LocalDateHolder holder) {
        validateRange(holder);
        // 会员必须存在（普通 find 即可，卡行锁是唯一串行点，避免会员锁+卡锁交叉导致死锁）
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + memberId));
        SeasonPass existing = seasonPassRepository.findByIdForUpdate(memberId).orElse(null);
        if (existing != null) {
            throw new BizException("该会员已持有年卡/季卡（" + existing.getPassType()
                    + "，有效期至 " + existing.getValidTo() + "），请使用续期接口");
        }
        SeasonPass pass = new SeasonPass();
        pass.setMemberId(memberId);
        pass.setMember(member);
        pass.setValidFrom(holder.validFrom);
        pass.setValidTo(holder.validTo);
        pass.setPassType(normalizePassType(holder.passType));
        pass.setCreatedAt(LocalDateTime.now(clock));
        try {
            // 必须用 persist + flush 插入新卡行：member_id 是应用赋值的共享主键，
            // save/saveAndFlush 会按“非自增主键”走 merge，而 Hibernate 6.5 对
            // @PrimaryKeyJoinColumn 共享主键一对一在 merge 时抛
            // AssertionFailure(null identifier)。重复主键由唯一约束兜底。
            entityManager.persist(pass);
            entityManager.flush();
            return pass;
        } catch (DataIntegrityViolationException e) {
            // 极端并发下行锁之外的第二道防线（共享主键唯一约束）
            throw new BizException("该会员已持有年卡/季卡，请刷新后使用续期接口");
        }
    }

    /**
     * 续期 / 换卡：实时覆盖有效期与卡类型并立即提交。乘坐核验每次重新读库，
     * 下一次核验立即按新日期生效，不依赖重启或任何缓存。
     */
    @Transactional
    public SeasonPass renew(Long memberId, LocalDateHolder holder) {
        validateRange(holder);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + memberId + "，无法续期"));
        SeasonPass pass = seasonPassRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BizException("未找到会员「" + member.getName()
                        + "」的年卡/季卡，无法续期，请先办卡"));
        pass.setValidFrom(holder.validFrom);
        pass.setValidTo(holder.validTo);
        pass.setPassType(normalizePassType(holder.passType));
        pass.setRenewedAt(LocalDateTime.now(clock));
        return seasonPassRepository.saveAndFlush(pass);
    }

    /** 日期区间校验：必填 + validFrom <= validTo（含首尾，起止可同为一天） */
    private void validateRange(LocalDateHolder holder) {
        if (holder == null) {
            throw new BizException("请填写有效期起止日期");
        }
        if (holder.validFrom == null) {
            throw new BizException("有效期起不能为空");
        }
        if (holder.validTo == null) {
            throw new BizException("有效期止不能为空");
        }
        if (holder.validFrom.isAfter(holder.validTo)) {
            throw new BizException("有效期起（" + holder.validFrom + "）不能晚于有效期止（"
                    + holder.validTo + "）；起止同一天也有效");
        }
    }

    private String normalizePassType(String passType) {
        if (passType == null || passType.isBlank()) {
            return "年卡";
        }
        String t = passType.trim();
        if (!Dict.isValidPassType(t)) {
            throw new BizException("卡类型不合法：" + t + "，仅支持 年卡 / 季卡");
        }
        return t;
    }

    /** 有效期区间载体（开放给 Controller 作为请求体）。 */
    public static class LocalDateHolder {
        public LocalDate validFrom;
        public LocalDate validTo;
        public String passType;

        public LocalDateHolder() {
        }
    }
}
