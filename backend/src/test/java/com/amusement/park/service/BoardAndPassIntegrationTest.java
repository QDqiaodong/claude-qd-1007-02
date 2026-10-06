package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.amusement.park.common.BizException;
import com.amusement.park.common.BoardRejectedException;
import com.amusement.park.dto.BoardResult;
import com.amusement.park.dto.MemberPassOption;
import com.amusement.park.entity.Member;
import com.amusement.park.entity.Ride;
import com.amusement.park.repository.MemberRepository;
import com.amusement.park.repository.RideRepository;
import com.amusement.park.repository.SeasonPassRepository;

/**
 * 乘坐核验 / 办卡 / 续期的仓储与事务集成测试（H2 内存库）。
 *
 * <p>时钟固定在 2026-06-15（Asia/Shanghai），保证日期首尾边界可重复；
 * 覆盖：未办卡/未生效/已过期/首尾当天、续期后立即生效（无缓存/不重启）、
 * 办卡日期区间与并发重复办卡、失败请求不回滚已成功结果，以及接口 message 与
 * 可解释结果 reason/逐项文案一致。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BoardAndPassIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 固定时钟：以同名 Bean {@code parkClock} 覆盖主配置里的 parkClock
     * （测试通过 allow-bean-definition-overriding 显式允许同名覆盖），
     * 不再新增第二个 {@code @Primary}，避免与 {@code ClockConfig} 里的 parkClock
     * 同时成为候选而报 “more than one 'primary' bean”。
     * 整个测试期间时间固定在园区时区 2026-06-15，保证日期首尾边界可重复。
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock parkClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired private BoardVerificationService boardService;
    @Autowired private SeasonPassService passService;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SeasonPassRepository passRepository;
    @Autowired private RideRepository rideRepository;
    @Autowired private MockMvc mockMvc;

    private long seq = 100;

    private Member newMember(String name, String tier) {
        Member m = new Member();
        m.setName(name + "-" + (seq++));
        m.setTier(tier);
        m.setPhone("139" + (seq));
        m.setCreatedAt(java.time.LocalDateTime.now());
        return memberRepository.save(m);
    }

    private Ride newOpenRide(int minHeight, Integer maxAge) {
        Ride r = new Ride();
        r.setRideCode("T-" + (seq++));
        r.setRideName("测试设施");
        r.setMinHeightCm(minHeight);
        r.setMaxAge(maxAge);
        r.setStatus("开放");
        r.setCreatedAt(java.time.LocalDateTime.now());
        return rideRepository.save(r);
    }

    private Ride newClosedRide() {
        Ride r = new Ride();
        r.setRideCode("T-" + (seq++));
        r.setRideName("关闭设施");
        r.setMinHeightCm(100);
        r.setMaxAge(60);
        r.setStatus("关闭");
        r.setCreatedAt(java.time.LocalDateTime.now());
        return rideRepository.save(r);
    }

    private SeasonPassService.LocalDateHolder dates(String from, String to, String type) {
        SeasonPassService.LocalDateHolder h = new SeasonPassService.LocalDateHolder();
        h.validFrom = LocalDate.parse(from);
        h.validTo = LocalDate.parse(to);
        h.passType = type;
        return h;
    }

    // ---------------- 乘坐核验：身份/卡/限制端到端 ----------------

    @Test
    @DisplayName("未办卡会员 → 拒绝；已过期 → 拒绝；续期覆盖今天后立即允许（同一上下文，不重启）")
    void expiredThenRenewTakesEffectImmediately() {
        Member m = newMember("过期会员", "金卡");
        Ride ride = newOpenRide(130, 55);
        passService.issue(m.getId(), dates("2026-01-01", "2026-05-31", "年卡"));

        // 已过期
        assertThatThrownBy(() -> boardService.verify(ride.getId(), 170, 30, m.getId()))
                .isInstanceOf(BoardRejectedException.class)
                .hasMessageContaining("已过期");

        // 续期到覆盖今天 → 下一次核验立即按新日期
        passService.renew(m.getId(), dates("2026-06-01", "2027-05-31", "年卡"));
        BoardResult r = boardService.verify(ride.getId(), 170, 30, m.getId());
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getPassType()).isEqualTo("年卡");
    }

    @Test
    @DisplayName("日期首尾边界：有效期止=今天、有效期起=今天 都允许（含首尾）")
    void inclusiveBoundaries() {
        Ride ride = newOpenRide(90, 80);
        Member lastDay = newMember("最后一天", "普通");
        Member firstDay = newMember("首日", "普通");
        passService.issue(lastDay.getId(), dates("2026-05-01", "2026-06-15", "季卡"));
        passService.issue(firstDay.getId(), dates("2026-06-15", "2026-09-15", "季卡"));

        assertThat(boardService.verify(ride.getId(), 120, 20, lastDay.getId()).isAllowed()).isTrue();
        assertThat(boardService.verify(ride.getId(), 120, 20, firstDay.getId()).isAllowed()).isTrue();
    }

    @Test
    @DisplayName("未生效卡拒绝；有效卡也不能绕过关闭设施")
    void notStartedAndClosedRide() {
        Member future = newMember("未来卡", "银卡");
        passService.issue(future.getId(), dates("2026-07-01", "2026-10-01", "季卡"));
        Ride ride = newOpenRide(100, 70);
        assertThatThrownBy(() -> boardService.verify(ride.getId(), 170, 30, future.getId()))
                .isInstanceOf(BoardRejectedException.class)
                .hasMessageContaining("尚未生效");

        Member active = newMember("有效卡", "金卡");
        passService.issue(active.getId(), dates("2026-01-01", "2026-12-31", "年卡"));
        Ride closed = newClosedRide();
        assertThatThrownBy(() -> boardService.verify(closed.getId(), 170, 30, active.getId()))
                .isInstanceOf(BoardRejectedException.class)
                .hasMessageContaining("关闭");
    }

    @Test
    @DisplayName("会员不存在 → 拒绝原因明确，不抛 500")
    void unknownMember() {
        Ride ride = newOpenRide(100, 70);
        assertThatThrownBy(() -> boardService.verify(ride.getId(), 170, 30, 999_999L))
                .isInstanceOf(BoardRejectedException.class)
                .hasMessageContaining("会员不存在");
    }

    // ---------------- 办卡 / 续期：校验、异常、不回滚 ----------------

    @Test
    @DisplayName("办卡：会员不存在、非法日期区间、缺日期 都拒绝且不落库；重复办卡被拦截")
    void issueValidations() {
        Member m = newMember("办卡会员", "普通");

        assertThatThrownBy(() -> passService.issue(999_999L, dates("2026-01-01", "2026-12-31", "年卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("会员不存在");
        assertThat(passRepository.existsById(999_999L)).isFalse();

        assertThatThrownBy(() -> passService.issue(m.getId(), dates("2026-12-31", "2026-01-01", "年卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("不能晚于");
        assertThat(passRepository.existsById(m.getId())).isFalse();

        SeasonPassService.LocalDateHolder missing = new SeasonPassService.LocalDateHolder();
        missing.validFrom = LocalDate.parse("2026-01-01");
        assertThatThrownBy(() -> passService.issue(m.getId(), missing))
                .isInstanceOf(BizException.class).hasMessageContaining("有效期止不能为空");
        assertThat(passRepository.existsById(m.getId())).isFalse();

        passService.issue(m.getId(), dates("2026-01-01", "2026-12-31", "年卡"));
        assertThatThrownBy(() -> passService.issue(m.getId(), dates("2027-01-01", "2027-12-31", "年卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("已持有");
        // 仍只有一张卡，原有效期未被第二次请求改动
        assertThat(passRepository.findById(m.getId()).orElseThrow().getValidTo())
                .isEqualTo(LocalDate.parse("2026-12-31"));
    }

    @Test
    @DisplayName("续期：卡不存在/会员不存在拒绝；非法区间拒绝且不回滚上一次成功续期")
    void renewValidationsNoRollback() {
        Member m = newMember("续期会员", "普通");
        passService.issue(m.getId(), dates("2026-01-01", "2026-06-30", "季卡"));

        assertThatThrownBy(() -> passService.renew(999_999L, dates("2026-07-01", "2026-12-31", "季卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("会员不存在");
        assertThatThrownBy(() -> passService.renew(m.getId(), dates("2026-12-31", "2026-01-01", "年卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("不能晚于");

        // 一次合法续期成功
        passService.renew(m.getId(), dates("2026-07-01", "2027-06-30", "年卡"));
        // 紧接的非法续期失败，不得把上一次成功的新日期回滚
        assertThatThrownBy(() -> passService.renew(m.getId(), dates("2028-01-01", "2027-01-01", "年卡")))
                .isInstanceOf(BizException.class);
        assertThat(passRepository.findById(m.getId()).orElseThrow().getValidFrom())
                .isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(passRepository.findById(m.getId()).orElseThrow().getValidTo())
                .isEqualTo(LocalDate.parse("2027-06-30"));
        assertThat(passRepository.findById(m.getId()).orElseThrow().getPassType()).isEqualTo("年卡");

        Member noPass = newMember("无卡会员", "金卡");
        assertThatThrownBy(() -> passService.renew(noPass.getId(), dates("2026-01-01", "2026-12-31", "年卡")))
                .isInstanceOf(BizException.class).hasMessageContaining("无法续期");
    }

    @Test
    @DisplayName("并发重复办卡：两个请求同时到达，只产生一张卡，一个成功一个拒绝")
    void concurrentIssueOnlyOneSucceeds() throws Exception {
        Member m = newMember("并发会员", "普通");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> a = () -> {
                passService.issue(m.getId(), dates("2026-01-01", "2026-12-31", "年卡"));
                return "ok";
            };
            List<Future<String>> futures = pool.invokeAll(List.of(a, a));
            int success = 0;
            int rejected = 0;
            for (Future<String> f : futures) {
                try {
                    assertThat(f.get()).isEqualTo("ok");
                    success++;
                } catch (Exception e) {
                    rejected++;
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(passRepository.findAll()).filteredOn(p -> p.getMemberId().equals(m.getId())).hasSize(1);
        } finally {
            pool.shutdown();
        }
    }

    // ---------------- 关联查询与接口/页面文案一致性 ----------------

    @Test
    @DisplayName("boarding-options 关联查询：未办卡/未生效/当前有效/已过期 状态按当天正确标记")
    void boardingOptionsStatus() {
        Member none = newMember("无卡", "金卡");
        Member notStarted = newMember("未生效", "普通");
        Member active = newMember("有效", "银卡");
        Member expired = newMember("过期", "普通");
        passService.issue(notStarted.getId(), dates("2026-07-01", "2026-10-01", "季卡"));
        passService.issue(active.getId(), dates("2026-01-01", "2026-12-31", "年卡"));
        passService.issue(expired.getId(), dates("2026-01-01", "2026-05-31", "年卡"));

        List<MemberPassOption> list = memberService.listPassOptions(null);
        assertThat(list).extracting(MemberPassOption::getMemberId).contains(none.getId());
        assertThat(statusOf(list, none.getId())).isEqualTo("未办卡");
        assertThat(statusOf(list, notStarted.getId())).isEqualTo("未生效");
        assertThat(statusOf(list, active.getId())).isEqualTo("当前有效");
        assertThat(statusOf(list, expired.getId())).isEqualTo("已过期");
    }

    @Test
    @DisplayName("HTTP：未办卡拒绝时 ok=false、message 与 data.reason 一致，且逐项明细可读（页面据此渲染）")
    void httpRejectionShapeConsistent() throws Exception {
        Member m = newMember("接口会员", "金卡");
        Ride ride = newOpenRide(130, 55);

        mockMvc.perform(post("/api/rides/{id}/board", ride.getId())
                        .param("heightCm", "170").param("age", "30")
                        .param("memberId", String.valueOf(m.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.containsString("尚未办理年卡/季卡")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("尚未办理年卡/季卡")))
                .andExpect(jsonPath("$.data.checks.length()").value(5))
                .andExpect(jsonPath("$.data.checks[1].pass").value(false));
    }

    @Test
    @DisplayName("HTTP：有效卡 + 身高年龄达标 → 200，data.allowed=true")
    void httpAllowed() throws Exception {
        Member m = newMember("接口通过会员", "银卡");
        Ride ride = newOpenRide(130, 55);
        passService.issue(m.getId(), dates("2026-01-01", "2026-12-31", "年卡"));

        mockMvc.perform(post("/api/rides/{id}/board", ride.getId())
                        .param("heightCm", "170").param("age", "30")
                        .param("memberId", String.valueOf(m.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.visitorType").value("MEMBER"));
    }

    private String statusOf(List<MemberPassOption> list, Long memberId) {
        return list.stream().filter(o -> o.getMemberId().equals(memberId)).findFirst()
                .orElseThrow().getPassStatus();
    }
}
