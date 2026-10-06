package com.amusement.park.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.BoardRejectedException;
import com.amusement.park.common.PassStatuses;
import com.amusement.park.dto.BoardResult;
import com.amusement.park.dto.CheckItem;
import com.amusement.park.entity.Member;
import com.amusement.park.entity.Ride;
import com.amusement.park.entity.SeasonPass;
import com.amusement.park.repository.MemberRepository;
import com.amusement.park.repository.RideRepository;
import com.amusement.park.repository.SeasonPassRepository;

/**
 * 乘坐核验：把「会员身份 — 卡片有效期 — 设施限制」合并为一次可解释结果。
 *
 * <p>规则口径：</p>
 * <ol>
 *   <li>未选会员 → 散客流程，仅核验设施状态、身高、年龄（与历史行为一致）；</li>
 *   <li>选择会员 → 必须实时读取该会员共享主键卡片（season_pass.member_id = member.id），
 *       只有园区时区当天落在 [valid_from, valid_to] 闭区间（含首尾）才算「当前有效」。
 *       会员不存在、未办卡、未生效、已过期均给出明确拒绝原因，
 *       会员等级（普通/银卡/金卡）绝不等同于有效卡；</li>
 *   <li>有效年卡/季卡只是购票/身份权益，不能绕过设施关闭、检修、身高、年龄限制，
 *       四类条件（有效卡 + 设施开放 + 身高达标 + 年龄达标）必须同时满足；</li>
 *   <li>每次都从数据库读取最新设施与卡片数据，续期后下一次核验立即按新日期生效，
 *       不依赖重启或缓存。</li>
 * </ol>
 *
 * <p>核验产生 5 个 {@link CheckItem}（会员身份/卡片有效期/设施状态/身高/年龄），
 * 第一项失败的拒绝原因即整体 reason，接口 message 与页面展示共用同一份文案。</p>
 */
@Service
public class BoardVerificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RideRepository rideRepository;
    private final MemberRepository memberRepository;
    private final SeasonPassRepository seasonPassRepository;
    private final Clock clock;

    public BoardVerificationService(RideRepository rideRepository,
                                    MemberRepository memberRepository,
                                    SeasonPassRepository seasonPassRepository,
                                    Clock clock) {
        this.rideRepository = rideRepository;
        this.memberRepository = memberRepository;
        this.seasonPassRepository = seasonPassRepository;
        this.clock = clock;
    }

    /**
     * 执行乘坐核验。
     *
     * @param memberId 会员 id；为 null 表示散客（保留原散客流程）
     * @return 全部满足时返回允许结果；任一不满足抛出 {@link BoardRejectedException}
     */
    @Transactional(readOnly = true)
    public BoardResult verify(Long rideId, Integer heightCm, Integer age, Long memberId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new BizException("设施不存在：" + rideId));
        LocalDate today = LocalDate.now(clock);

        BoardResult result = new BoardResult();
        result.setRideId(ride.getId());
        result.setRideName(ride.getRideName());
        result.setRideStatus(ride.getStatus());

        List<CheckItem> checks = new ArrayList<>();

        Member member = null;
        SeasonPass pass = null;
        if (memberId == null) {
            result.setVisitorType("GUEST");
            checks.add(CheckItem.skip("member", "会员身份", "散客，未选择会员"));
            checks.add(CheckItem.skip("pass", "卡片有效期", "散客不核验年卡/季卡"));
        } else {
            result.setVisitorType("MEMBER");
            member = memberRepository.findById(memberId).orElse(null);
            if (member == null) {
                checks.add(CheckItem.fail("member", "会员身份",
                        "所选会员 ID：" + memberId,
                        "所选会员不存在（ID：" + memberId + "），请重新选择会员后再核验"));
                checks.add(CheckItem.skip("pass", "卡片有效期", "会员不存在，未能读取年卡/季卡"));
            } else {
                result.setMemberId(member.getId());
                result.setMemberName(member.getName());
                result.setMemberTier(member.getTier());
                checks.add(CheckItem.ok("member", "会员身份",
                        "会员「" + member.getName() + "」，等级「" + member.getTier() + "」"
                                + "（等级仅为身份标识，不等同于有效卡）"));
                pass = seasonPassRepository.findById(memberId).orElse(null);
                checks.add(passCheck(pass, member, today));
                if (pass != null && PassStatuses.isValidOn(pass.getValidFrom(), pass.getValidTo(), today)) {
                    result.setPassType(pass.getPassType());
                }
            }
        }

        // 设施状态
        if ("开放".equals(ride.getStatus())) {
            checks.add(CheckItem.ok("rideStatus", "设施状态", "设施当前为「开放」"));
        } else {
            checks.add(CheckItem.fail("rideStatus", "设施状态",
                    "设施当前为「" + ride.getStatus() + "」",
                    "设施「" + ride.getRideName() + "」当前为「" + ride.getStatus()
                            + "」，未开放无法乘坐（年卡/季卡不能绕过）"));
        }

        // 身高
        if (heightCm == null) {
            checks.add(CheckItem.fail("height", "身高", "未填写身高", "请填写游客身高（厘米）"));
        } else if (ride.getMinHeightCm() != null && heightCm < ride.getMinHeightCm()) {
            checks.add(CheckItem.fail("height", "身高",
                    "身高 " + heightCm + "cm，要求 ≥ " + ride.getMinHeightCm() + "cm",
                    "身高 " + heightCm + "cm 不足，该设施要求至少 " + ride.getMinHeightCm()
                            + "cm（年卡/季卡不能绕过身高限制）"));
        } else {
            checks.add(CheckItem.ok("height", "身高",
                    ride.getMinHeightCm() == null
                            ? "身高 " + heightCm + "cm，该设施不限身高"
                            : "身高 " + heightCm + "cm ≥ 最低 " + ride.getMinHeightCm() + "cm"));
        }

        // 年龄
        if (age == null) {
            checks.add(CheckItem.fail("age", "年龄", "未填写年龄", "请填写游客年龄"));
        } else if (ride.getMaxAge() != null && age > ride.getMaxAge()) {
            checks.add(CheckItem.fail("age", "年龄",
                    "年龄 " + age + " 岁，要求 ≤ " + ride.getMaxAge() + " 岁",
                    "年龄 " + age + " 岁超过限制，该设施仅限 " + ride.getMaxAge()
                            + " 岁以下游客（年卡/季卡不能绕过年龄限制）"));
        } else {
            checks.add(CheckItem.ok("age", "年龄",
                    ride.getMaxAge() == null
                            ? "年龄 " + age + " 岁，该设施不限年龄"
                            : "年龄 " + age + " 岁 ≤ 上限 " + ride.getMaxAge() + " 岁"));
        }

        result.setChecks(checks);
        CheckItem firstFailure = checks.stream().filter(c -> !c.isPass() && !c.isSkipped()).findFirst().orElse(null);
        if (firstFailure != null) {
            result.setAllowed(false);
            result.setReason(firstFailure.getMessage());
            throw new BoardRejectedException(result);
        }
        result.setAllowed(true);
        result.setReason("核验通过，可以乘坐");
        return result;
    }

    /** 卡片有效期单项核验：未办卡 / 未生效 / 已过期 / 当前有效 */
    private CheckItem passCheck(SeasonPass pass, Member member, LocalDate today) {
        String tierText = "（会员等级「" + member.getTier() + "」不是有效卡）";
        if (pass == null) {
            return CheckItem.fail("pass", "卡片有效期",
                    "会员「" + member.getName() + "」无共享主键卡片记录",
                    "会员「" + member.getName() + "」尚未办理年卡/季卡" + tierText
                            + "，请先办卡或改用散客核验");
        }
        String range = pass.getPassType() + " " + fmt(pass.getValidFrom()) + " ~ " + fmt(pass.getValidTo());
        String status = PassStatuses.classify(pass.getValidFrom(), pass.getValidTo(), today);
        return switch (status) {
            case PassStatuses.ACTIVE -> CheckItem.ok("pass", "卡片有效期",
                    range + "，今天 " + fmt(today) + " 在有效期内（含起止当天）");
            case PassStatuses.NOT_STARTED -> CheckItem.fail("pass", "卡片有效期",
                    range + "，今天 " + fmt(today) + " 早于有效期起",
                    pass.getPassType() + "尚未生效（有效期自 " + fmt(pass.getValidFrom())
                            + " 起，含当天），生效前不能凭卡乘坐");
            default -> CheckItem.fail("pass", "卡片有效期",
                    range + "，今天 " + fmt(today) + " 晚于有效期止",
                    pass.getPassType() + "已过期（有效期至 " + fmt(pass.getValidTo())
                            + "，含当天），请续期后再乘坐");
        };
    }

    private static String fmt(LocalDate d) {
        return d == null ? "—" : d.format(DATE_FMT);
    }
}
