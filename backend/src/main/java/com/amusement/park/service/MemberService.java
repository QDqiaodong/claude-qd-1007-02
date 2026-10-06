package com.amusement.park.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.Dict;
import com.amusement.park.common.PassStatuses;
import com.amusement.park.dto.MemberPassOption;
import com.amusement.park.entity.Member;
import com.amusement.park.entity.SeasonPass;
import com.amusement.park.repository.MemberRepository;
import com.amusement.park.repository.SeasonPassRepository;

/**
 * 会员业务：园区会员基础档案，含 普通 / 银卡 / 金卡 三个等级。
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final SeasonPassRepository seasonPassRepository;
    private final Clock clock;

    public MemberService(MemberRepository memberRepository,
                         SeasonPassRepository seasonPassRepository,
                         Clock clock) {
        this.memberRepository = memberRepository;
        this.seasonPassRepository = seasonPassRepository;
        this.clock = clock;
    }

    public List<Member> list(String tier) {
        if (tier != null && !tier.isBlank()) {
            return memberRepository.findAll().stream()
                    .filter(m -> tier.equals(m.getTier()))
                    .toList();
        }
        return memberRepository.findAll();
    }

    /**
     * 乘坐弹窗会员选项（会员 LEFT JOIN 共享主键卡片的关联查询）。
     * 卡片状态按园区当天实时回填；未办卡标记为「未办卡」，等级不参与有效性判断。
     */
    @Transactional(readOnly = true)
    public List<MemberPassOption> listPassOptions(String tier) {
        LocalDate today = LocalDate.now(clock);
        List<Member> members = (tier != null && !tier.isBlank())
                ? memberRepository.findByTierOrderById(tier.trim())
                : memberRepository.findAll();
        Map<Long, SeasonPass> passByMember = seasonPassRepository.findAll().stream()
                .collect(Collectors.toMap(SeasonPass::getMemberId, Function.identity()));
        return members.stream().map(m -> {
            MemberPassOption o = new MemberPassOption();
            o.setMemberId(m.getId());
            o.setName(m.getName());
            o.setTier(m.getTier());
            o.setPhone(m.getPhone());
            SeasonPass p = passByMember.get(m.getId());
            if (p == null) {
                o.setPassStatus("未办卡");
            } else {
                o.setValidFrom(p.getValidFrom());
                o.setValidTo(p.getValidTo());
                o.setPassType(p.getPassType());
                o.setPassStatus(PassStatuses.classify(p.getValidFrom(), p.getValidTo(), today));
            }
            return o;
        }).toList();
    }

    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
    }

    @Transactional
    public Member create(Member input) {
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("会员姓名不能为空");
        }
        if (!Dict.isValidMemberTier(input.getTier())) {
            throw new BizException("会员等级不合法：" + input.getTier());
        }
        Member member = new Member();
        member.setName(input.getName().trim());
        member.setTier(input.getTier());
        member.setPhone(input.getPhone());
        member.setCreatedAt(LocalDateTime.now());
        return memberRepository.save(member);
    }

    @Transactional
    public Member update(Long id, Member input) {
        Member member = get(id);
        if (input.getName() != null && !input.getName().isBlank()) {
            member.setName(input.getName().trim());
        }
        if (input.getTier() != null && !input.getTier().isBlank()) {
            if (!Dict.isValidMemberTier(input.getTier())) {
                throw new BizException("会员等级不合法：" + input.getTier());
            }
            member.setTier(input.getTier());
        }
        if (input.getPhone() != null) {
            member.setPhone(input.getPhone());
        }
        return memberRepository.save(member);
    }
}
