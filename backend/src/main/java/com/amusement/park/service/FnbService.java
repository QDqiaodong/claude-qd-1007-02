package com.amusement.park.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.Dict;
import com.amusement.park.entity.Fnb;
import com.amusement.park.repository.FnbRepository;

/**
 * 餐饮网点业务：快餐 / 正餐 / 饮品多业态经营，状态在 营业 / 暂停 / 关闭 间流转。
 */
@Service
public class FnbService {

    private final FnbRepository fnbRepository;

    public FnbService(FnbRepository fnbRepository) {
        this.fnbRepository = fnbRepository;
    }

    public List<Fnb> list(String type, String status) {
        if (type != null && !type.isBlank() && status != null && !status.isBlank()) {
            return fnbRepository.findAll().stream()
                    .filter(f -> type.equals(f.getType()) && status.equals(f.getStatus()))
                    .toList();
        }
        if (type != null && !type.isBlank()) {
            return fnbRepository.findByType(type);
        }
        if (status != null && !status.isBlank()) {
            return fnbRepository.findByStatus(status);
        }
        return fnbRepository.findAll();
    }

    public Fnb get(Long id) {
        return fnbRepository.findById(id)
                .orElseThrow(() -> new BizException("餐饮网点不存在：" + id));
    }

    @Transactional
    public Fnb create(Fnb input) {
        if (input.getOutletCode() == null || input.getOutletCode().isBlank()) {
            throw new BizException("网点编号不能为空");
        }
        if (!fnbRepository.findByOutletCode(input.getOutletCode().trim()).isEmpty()) {
            throw new BizException("网点编号已存在：" + input.getOutletCode());
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("网点名称不能为空");
        }
        if (!Dict.isValidFnbType(input.getType())) {
            throw new BizException("餐饮业态不合法：" + input.getType());
        }
        Fnb fnb = new Fnb();
        fnb.setOutletCode(input.getOutletCode().trim());
        fnb.setName(input.getName().trim());
        fnb.setType(input.getType());
        fnb.setStatus(input.getStatus() == null || input.getStatus().isBlank() ? "营业" : input.getStatus());
        if (!Dict.isValidFnbStatus(fnb.getStatus())) {
            throw new BizException("网点状态不合法：" + fnb.getStatus());
        }
        fnb.setCreatedAt(LocalDateTime.now());
        return fnbRepository.save(fnb);
    }

    @Transactional
    public Fnb update(Long id, Fnb input) {
        Fnb fnb = get(id);
        if (input.getName() != null && !input.getName().isBlank()) {
            fnb.setName(input.getName().trim());
        }
        if (input.getType() != null && !input.getType().isBlank()) {
            if (!Dict.isValidFnbType(input.getType())) {
                throw new BizException("餐饮业态不合法：" + input.getType());
            }
            fnb.setType(input.getType());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            if (!Dict.isValidFnbStatus(input.getStatus())) {
                throw new BizException("网点状态不合法：" + input.getStatus());
            }
            fnb.setStatus(input.getStatus());
        }
        return fnbRepository.save(fnb);
    }

    /** 状态流转：营业 ↔ 暂停，营业/暂停 → 关闭，关闭 → 营业 */
    @Transactional
    public Fnb transfer(Long id, String target) {
        Fnb fnb = get(id);
        if (!Dict.isValidFnbStatus(target)) {
            throw new BizException("网点状态不合法：" + target);
        }
        if (!Dict.canTransferFnb(fnb.getStatus(), target)) {
            throw new BizException("不允许从「" + fnb.getStatus() + "」转为「" + target + "」");
        }
        fnb.setStatus(target);
        return fnbRepository.save(fnb);
    }
}
