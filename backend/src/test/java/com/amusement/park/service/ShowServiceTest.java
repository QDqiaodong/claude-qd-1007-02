package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amusement.park.common.BizException;
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowScheduleItem;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;

/**
 * {@link ShowService} 排序与时刻归一纯单测（Mockito 造数，不依赖数据库返回顺序）。
 *
 * <p>覆盖：按园区真实时刻升序、无时刻记录统一末尾、相同时刻按演出编号定序、
 * 带/不带偏移量写入时归一为一致的上海时刻且原文可追溯、未排期/清空排期、
 * 闰日与跨日场次排序位置。</p>
 */
class ShowServiceTest {

    private ShowRepository showRepository;
    private ShowService service;

    @BeforeEach
    void setUp() {
        showRepository = Mockito.mock(ShowRepository.class);
        Clock clock = Clock.fixed(
                LocalDateTime.of(2026, 6, 15, 12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        service = new ShowService(showRepository, clock);
    }

    private Show show(long id, String code, String raw, LocalDateTime parkAt) {
        Show s = new Show();
        s.setId(id);
        s.setShowCode(code);
        s.setShowName("演出" + code);
        s.setCapacity(100);
        s.setBooked(0);
        s.setShowAtRaw(raw);
        s.setShowAt(parkAt);
        return s;
    }

    private void givenRows(Show... rows) {
        // 故意用乱序返回，证明排序不依赖数据库顺序
        List<Show> shuffled = new ArrayList<>(List.of(rows));
        java.util.Collections.reverse(shuffled);
        when(showRepository.findAll()).thenReturn(shuffled);
    }

    private List<String> codes(List<ShowScheduleItem> items) {
        return items.stream().map(ShowScheduleItem::getShowCode).toList();
    }

    @Test
    @DisplayName("排序：按园区时刻从早到晚，与仓储返回顺序无关")
    void sortByParkTimeAscending() {
        givenRows(
                show(3, "S-003", null, LocalDateTime.of(2028, 3, 4, 11, 0)),
                show(1, "S-001", null, LocalDateTime.of(2028, 3, 3, 10, 0)),
                show(2, "S-002", null, LocalDateTime.of(2028, 3, 3, 14, 30)));

        assertThat(codes(service.listSchedule())).containsExactly("S-001", "S-002", "S-003");
    }

    @Test
    @DisplayName("无时刻记录统一放在末尾；多条无时刻记录之间按编号定序")
    void nullTimesGoLastAndOrderedByCode() {
        givenRows(
                show(4, "S-004", null, LocalDateTime.of(2028, 3, 4, 9, 0)),
                show(2, "S-011", null, null),
                show(1, "S-007", null, null),
                show(3, "S-001", null, LocalDateTime.of(2028, 3, 3, 8, 0)));

        assertThat(codes(service.listSchedule()))
                .containsExactly("S-001", "S-004", "S-007", "S-011");
    }

    @Test
    @DisplayName("同一园区时刻：按演出编号升序，确定且可重复（与原始写法带不带偏移无关）")
    void sameInstantTieBreakByShowCode() {
        LocalDateTime same = LocalDateTime.of(2028, 3, 3, 7, 30);
        givenRows(
                show(2, "S-012", "2028-03-03 07:30:00", same),
                show(3, "S-013", null, same),
                show(1, "S-009", "2028-03-02T23:30:00Z", same));

        assertThat(codes(service.listSchedule())).containsExactly("S-009", "S-012", "S-013");
    }

    @Test
    @DisplayName("跨日午夜场次按解释后的园区日期排序：UTC 23:30 归 3/3，排在 3/4 之前")
    void crossedMidnightOrderedByParkDay() {
        givenRows(
                show(2, "S-010", null, LocalDateTime.of(2028, 3, 4, 0, 30)),
                show(1, "S-009", "2028-03-02T23:30:00Z",
                        LocalDateTime.of(2028, 3, 3, 7, 30)));

        assertThat(codes(service.listSchedule())).containsExactly("S-009", "S-010");
    }

    @Test
    @DisplayName("闰日场次正确参与排序（2-29 早于 3-3）")
    void leapDaySortsCorrectly() {
        givenRows(
                show(2, "S-008", null, LocalDateTime.of(2028, 2, 29, 10, 30)),
                show(1, "S-001", null, LocalDateTime.of(2028, 3, 3, 10, 0)));

        assertThat(codes(service.listSchedule())).containsExactly("S-008", "S-001");
    }

    @Test
    @DisplayName("列表项显式给出园区日期、时间与时区标签，未排期三项为 null")
    void itemCarriesExplicitParkDateAndTime() {
        givenRows(show(1, "S-009", "2028-03-02T23:30:00Z",
                LocalDateTime.of(2028, 3, 3, 7, 30)));

        ShowScheduleItem item = service.listSchedule().get(0);
        assertThat(item.getParkDate()).isEqualTo("2028-03-03");
        assertThat(item.getParkTime()).isEqualTo("07:30");
        assertThat(item.getShowAt()).isEqualTo("2028-03-03T07:30:00");
        assertThat(item.getParkZone()).isEqualTo("Asia/Shanghai");
        assertThat(item.getParkZoneLabel()).isEqualTo("UTC+08:00（上海）");
        assertThat(item.getShowAtRaw()).isEqualTo("2028-03-02T23:30:00Z");
    }

    @Test
    @DisplayName("未排期列表项园区日期/时间/时刻均为 null")
    void unscheduledItemHasNullParkFields() {
        givenRows(show(1, "S-011", null, null));

        ShowScheduleItem item = service.listSchedule().get(0);
        assertThat(item.getParkDate()).isNull();
        assertThat(item.getParkTime()).isNull();
        assertThat(item.getShowAt()).isNull();
    }

    // ---------------- 写入时归一 ----------------

    @Test
    @DisplayName("建档：不带偏移量按上海挂钟入库，原文同时保存")
    void createNormalizesNaiveRaw() {
        ShowInput input = new ShowInput();
        input.setShowCode("S-100");
        input.setShowName("测试演出");
        input.setCapacity(100);
        input.setShowAtRaw("2028-03-03 07:30:00");
        when(showRepository.save(Mockito.any(Show.class))).thenAnswer(inv -> inv.getArgument(0));

        Show saved = service.create(input);

        assertThat(saved.getShowAt()).isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
        assertThat(saved.getShowAtRaw()).isEqualTo("2028-03-03 07:30:00");
    }

    @Test
    @DisplayName("建档：带偏移量（夏令时 -07:00）换算为上海时刻，原文保留可追溯")
    void createNormalizesOffsetRaw() {
        ShowInput input = new ShowInput();
        input.setShowCode("S-101");
        input.setShowName("测试演出");
        input.setCapacity(100);
        input.setShowAtRaw("2028-07-15T10:00:00-07:00");
        when(showRepository.save(Mockito.any(Show.class))).thenAnswer(inv -> inv.getArgument(0));

        Show saved = service.create(input);

        assertThat(saved.getShowAt()).isEqualTo(LocalDateTime.of(2028, 7, 16, 1, 0, 0));
        assertThat(saved.getShowAtRaw()).isEqualTo("2028-07-15T10:00:00-07:00");
    }

    @Test
    @DisplayName("建档：空时刻 → showAt 与原文均为 null（未排期）")
    void createWithEmptyRawLeavesNull() {
        ShowInput input = new ShowInput();
        input.setShowCode("S-102");
        input.setShowName("测试演出");
        input.setCapacity(100);
        input.setShowAtRaw("");
        when(showRepository.save(Mockito.any(Show.class))).thenAnswer(inv -> inv.getArgument(0));

        Show saved = service.create(input);

        assertThat(saved.getShowAt()).isNull();
        assertThat(saved.getShowAtRaw()).isNull();
    }

    @Test
    @DisplayName("建档：非法时刻字面量 → 中文业务异常")
    void createWithInvalidRawRejected() {
        ShowInput input = new ShowInput();
        input.setShowCode("S-103");
        input.setShowName("测试演出");
        input.setCapacity(100);
        input.setShowAtRaw("明天晚上八点");

        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("演出时刻格式无法识别");
    }

    @Test
    @DisplayName("更新：传入空串清空排期；传入新字面量重新解释")
    void updateCanClearOrReschedule() {
        Show existing = show(1L, "S-001", "2028-03-03 07:30:00",
                LocalDateTime.of(2028, 3, 3, 7, 30));
        when(showRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(showRepository.save(Mockito.any(Show.class))).thenAnswer(inv -> inv.getArgument(0));

        ShowInput clear = new ShowInput();
        clear.setShowAtRaw("");
        Show cleared = service.update(1L, clear);
        assertThat(cleared.getShowAt()).isNull();
        assertThat(cleared.getShowAtRaw()).isNull();

        ShowInput reschedule = new ShowInput();
        reschedule.setShowAtRaw("2028-03-02T23:30:00Z");
        Show moved = service.update(1L, reschedule);
        assertThat(moved.getShowAt()).isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30));
        assertThat(moved.getShowAtRaw()).isEqualTo("2028-03-02T23:30:00Z");
    }
}
