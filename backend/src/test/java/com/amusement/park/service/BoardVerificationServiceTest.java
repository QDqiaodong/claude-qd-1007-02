package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amusement.park.common.BoardRejectedException;
import com.amusement.park.dto.BoardResult;
import com.amusement.park.dto.CheckItem;
import com.amusement.park.entity.Member;
import com.amusement.park.entity.Ride;
import com.amusement.park.entity.SeasonPass;
import com.amusement.park.repository.MemberRepository;
import com.amusement.park.repository.RideRepository;
import com.amusement.park.repository.SeasonPassRepository;

/**
 * 乘坐核验纯单测（Mockito 造数 + 固定时钟）：覆盖会员不存在、未办卡、
 * 未生效/已过期/当前有效（含首尾边界）、金卡等级不等于有效卡，以及
 * 有效卡不能绕过关闭/检修、身高、年龄限制。
 */
class BoardVerificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private RideRepository rideRepository;
    private MemberRepository memberRepository;
    private SeasonPassRepository seasonPassRepository;
    private BoardVerificationService service;

    @BeforeEach
    void setUp() {
        rideRepository = Mockito.mock(RideRepository.class);
        memberRepository = Mockito.mock(MemberRepository.class);
        seasonPassRepository = Mockito.mock(SeasonPassRepository.class);
        Clock fixed = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        service = new BoardVerificationService(rideRepository, memberRepository, seasonPassRepository, fixed);
    }

    private Ride ride(long id, String name, String status, Integer minHeight, Integer maxAge) {
        Ride r = new Ride();
        r.setId(id);
        r.setRideCode("R-" + id);
        r.setRideName(name);
        r.setStatus(status);
        r.setMinHeightCm(minHeight);
        r.setMaxAge(maxAge);
        return r;
    }

    private Member member(long id, String name, String tier) {
        Member m = new Member();
        m.setId(id);
        m.setName(name);
        m.setTier(tier);
        return m;
    }

    private SeasonPass pass(long id, LocalDate from, LocalDate to, String type) {
        SeasonPass p = new SeasonPass();
        p.setMemberId(id);
        p.setValidFrom(from);
        p.setValidTo(to);
        p.setPassType(type);
        return p;
    }

    private CheckItem item(BoardResult r, String key) {
        return r.getChecks().stream().filter(c -> c.getKey().equals(key)).findFirst().orElseThrow();
    }

    // ---------------- 散客（保留原流程） ----------------

    @Test
    @DisplayName("散客：未选会员，开放且身高年龄达标 → 通过，会员/卡片两项跳过")
    void guestAllowed() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));

        BoardResult r = service.verify(1L, 170, 30, null);

        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getVisitorType()).isEqualTo("GUEST");
        assertThat(item(r, "member").isSkipped()).isTrue();
        assertThat(item(r, "pass").isSkipped()).isTrue();
        assertThat(item(r, "rideStatus").isPass()).isTrue();
        assertThat(item(r, "height").isPass()).isTrue();
        assertThat(item(r, "age").isPass()).isTrue();
    }

    @Test
    @DisplayName("散客：设施关闭 → 拒绝，原因指向设施状态")
    void guestRideClosed() {
        when(rideRepository.findById(7L)).thenReturn(Optional.of(ride(7, "海盗船", "关闭", 110, 65)));

        assertThatThrownBy(() -> service.verify(7L, 170, 30, null))
                .isInstanceOf(BoardRejectedException.class)
                .hasMessageContaining("海盗船")
                .hasMessageContaining("关闭");
    }

    // ---------------- 会员身份 ----------------

    @Test
    @DisplayName("选择了不存在的会员 → 明确拒绝，不读取等级/卡")
    void memberNotFound() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 170, 30, 999L));
        BoardResult r = ex.getResult();
        assertThat(r.isAllowed()).isFalse();
        assertThat(item(r, "member").isPass()).isFalse();
        assertThat(r.getReason()).isEqualTo(item(r, "member").getMessage());
        assertThat(r.getReason()).contains("会员不存在");
        assertThat(item(r, "pass").isSkipped()).isTrue();
    }

    @Test
    @DisplayName("会员存在但未办卡 → 拒绝原因写明未办卡，并提示等级不是有效卡")
    void memberWithoutPass() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(4L)).thenReturn(Optional.of(member(4, "赵明轩", "金卡")));
        when(seasonPassRepository.findById(4L)).thenReturn(Optional.empty());

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 170, 30, 4L));
        assertThat(ex.getMessage()).contains("尚未办理年卡/季卡").contains("金卡");
        BoardResult r = ex.getResult();
        assertThat(item(r, "member").isPass()).isTrue();
        assertThat(item(r, "pass").isPass()).isFalse();
    }

    // ---------------- 卡片有效期边界（含首尾） ----------------

    @Test
    @DisplayName("今天等于有效期起（首）→ 有效")
    void passValidOnFirstDay() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member(2, "陈志远", "银卡")));
        when(seasonPassRepository.findById(2L))
                .thenReturn(Optional.of(pass(2, TODAY, TODAY.plusDays(20), "季卡")));

        BoardResult r = service.verify(1L, 170, 30, 2L);
        assertThat(r.isAllowed()).isTrue();
        assertThat(item(r, "pass").isPass()).isTrue();
        assertThat(r.getPassType()).isEqualTo("季卡");
    }

    @Test
    @DisplayName("今天等于有效期止（尾）→ 有效")
    void passValidOnLastDay() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member(2, "陈志远", "银卡")));
        when(seasonPassRepository.findById(2L))
                .thenReturn(Optional.of(pass(2, TODAY.minusDays(30), TODAY, "季卡")));

        BoardResult r = service.verify(1L, 170, 30, 2L);
        assertThat(r.isAllowed()).isTrue();
        assertThat(item(r, "pass").isPass()).isTrue();
    }

    @Test
    @DisplayName("尚未生效（今天早于有效期起）→ 拒绝，不能凭卡乘坐")
    void passNotStarted() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(member(5, "孙佳怡", "金卡")));
        when(seasonPassRepository.findById(5L))
                .thenReturn(Optional.of(pass(5, TODAY.plusDays(1), TODAY.plusDays(45), "季卡")));

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 170, 30, 5L));
        assertThat(ex.getMessage()).contains("尚未生效");
    }

    @Test
    @DisplayName("已过期（今天晚于有效期止）→ 拒绝，提示续期")
    void passExpired() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(member(5, "孙佳怡", "金卡")));
        when(seasonPassRepository.findById(5L))
                .thenReturn(Optional.of(pass(5, TODAY.minusDays(60), TODAY.minusDays(1), "季卡")));

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 170, 30, 5L));
        assertThat(ex.getMessage()).contains("已过期").contains("续期");
    }

    // ---------------- 有效卡不能绕过设施限制（四类条件同时满足） ----------------

    @Test
    @DisplayName("有效卡 + 设施检修 → 仍然拒绝")
    void validPassCannotBypassInspection() {
        when(rideRepository.findById(3L)).thenReturn(Optional.of(ride(3, "高空跳楼机", "检修", 140, 50)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1, "林晓彤", "金卡")));
        when(seasonPassRepository.findById(1L))
                .thenReturn(Optional.of(pass(1, TODAY.minusDays(10), TODAY.plusDays(10), "年卡")));

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(3L, 170, 30, 1L));
        assertThat(ex.getMessage()).contains("检修").contains("不能绕过");
        BoardResult r = ex.getResult();
        assertThat(item(r, "pass").isPass()).isTrue();
        assertThat(item(r, "rideStatus").isPass()).isFalse();
    }

    @Test
    @DisplayName("有效卡 + 身高不足 → 仍然拒绝")
    void validPassCannotBypassHeight() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1, "林晓彤", "金卡")));
        when(seasonPassRepository.findById(1L))
                .thenReturn(Optional.of(pass(1, TODAY.minusDays(10), TODAY.plusDays(10), "年卡")));

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 120, 30, 1L));
        assertThat(ex.getMessage()).contains("身高").contains("不能绕过");
    }

    @Test
    @DisplayName("有效卡 + 年龄超上限 → 仍然拒绝")
    void validPassCannotBypassAge() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1, "林晓彤", "金卡")));
        when(seasonPassRepository.findById(1L))
                .thenReturn(Optional.of(pass(1, TODAY.minusDays(10), TODAY.plusDays(10), "年卡")));

        BoardRejectedException ex = (BoardRejectedException) catchEx(() -> service.verify(1L, 170, 60, 1L));
        assertThat(ex.getMessage()).contains("年龄").contains("不能绕过");
    }

    @Test
    @DisplayName("有效卡 + 开放 + 身高年龄全达标 → 四类条件同时满足，允许")
    void memberFullyAllowed() {
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride(1, "极速过山车", "开放", 130, 55)));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1, "林晓彤", "金卡")));
        when(seasonPassRepository.findById(1L))
                .thenReturn(Optional.of(pass(1, TODAY.minusDays(10), TODAY.plusDays(10), "年卡")));

        BoardResult r = service.verify(1L, 170, 30, 1L);
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getVisitorType()).isEqualTo("MEMBER");
        assertThat(r.getMemberName()).isEqualTo("林晓彤");
        assertThat(r.getChecks()).filteredOn(c -> !c.isSkipped()).allMatch(CheckItem::isPass);
    }

    private BoardRejectedException catchEx(Runnable r) {
        try {
            r.run();
            throw new AssertionError("期望抛出 BoardRejectedException");
        } catch (BoardRejectedException e) {
            return e;
        }
    }
}
