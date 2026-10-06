package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amusement.park.common.BizException;
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowListItem;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;

/**
 * 园区演出列表解释/排序的服务层单测（不经过数据库，可安全切换 JVM 默认时区）。
 *
 * <p>这里直接切换 JVM 默认时区（UTC、美洲夏令时、印度、上海），证明列表的园区
 * 日期归属、展示文本与稳定次序只依赖固定的园区时区，不依赖系统默认时区——
 * 与 {@link com.amusement.park.common.ParkShowTimeTest} 共同覆盖“多个客户端/系统
 * 时区结果一致”的要求；仓储层的端到端覆盖见 ShowListIntegrationTest。</p>
 */
class ShowServiceTest {

    private ShowRepository repository;
    private ShowService service;
    private TimeZone savedDefault;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ShowRepository.class);
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 6, 15, 12, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        service = new ShowService(repository, clock);
        savedDefault = TimeZone.getDefault();
        // 仓储模拟：save 原样回填（含 id），findAll 返回当前库内副本
        when(repository.save(any(Show.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(savedDefault);
    }

    private List<Show> db = new ArrayList<>();

    private Show persist(String code, Long id, LocalDateTime parkTime, String source, String offset) {
        Show s = new Show();
        s.setId(id);
        s.setShowCode(code);
        s.setShowName("演出-" + code);
        s.setVenue("场地");
        s.setCapacity(100);
        s.setBooked(0);
        s.setShowAt(parkTime);
        s.setTimeSource(source);
        s.setTimeOffset(offset);
        db.add(s);
        return s;
    }

    private void bindFindAll() {
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(db));
    }

    private void underZones(String[] zones, Runnable body) {
        for (String zone : zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            body.run();
        }
    }

    @Test
    @DisplayName("列表次序与园区字段在 UTC/美洲夏令时/印度/上海 默认时区下完全一致")
    void orderAndParkFieldsStableAcrossJvmZones() {
        // 已按园区时区解释好的实体（模拟仓储返回），含跨日/闰日/同刻/无时刻
        persist("S-A10", 10L, LocalDateTime.of(2024, 2, 29, 20, 0), "2024-02-29T20:00:00", "BARE");
        persist("S-A03", 3L, LocalDateTime.of(2024, 3, 1, 0, 30), "2024-02-29T16:30:00Z", "Z");
        persist("S-A04", 4L, LocalDateTime.of(2024, 3, 1, 0, 30), "2024-02-29T11:30:00-05:00", "-05:00");
        persist("S-A05", 5L, LocalDateTime.of(2024, 3, 2, 19, 0), "2024-03-02T15:00:00+04:00", "+04:00");
        persist("S-A01", 1L, LocalDateTime.of(2024, 2, 28, 10, 0), "2024-02-28T10:00:00", "BARE");
        persist("S-NULL", 7L, null, null, null);
        bindFindAll();

        List<String> expectedCodes = List.of("S-A01", "S-A10", "S-A03", "S-A04", "S-A05", "S-NULL");

        underZones(new String[]{"UTC", "America/New_York", "Asia/Kolkata", "Asia/Shanghai"}, () -> {
            List<ShowListItem> list = service.list();
            assertThat(list).extracting(ShowListItem::getShowCode).containsExactlyElementsOf(expectedCodes);

            ShowListItem crossDay = list.get(2);
            assertThat(crossDay.getShowCode()).isEqualTo("S-A03");
            assertThat(crossDay.getParkDate()).isEqualTo("2024-03-01");
            assertThat(crossDay.getParkTime()).isEqualTo("00:30");
            assertThat(crossDay.getParkDateTime()).isEqualTo("2024-03-01 00:30");
            assertThat(crossDay.getShowAt()).isEqualTo("2024-03-01T00:30:00");
            assertThat(crossDay.getTimeOffset()).isEqualTo("Z");

            ShowListItem leap = list.get(1);
            assertThat(leap.getParkDate()).isEqualTo("2024-02-29");

            ShowListItem unscheduled = list.get(5);
            assertThat(unscheduled.isScheduled()).isFalse();
            assertThat(unscheduled.getParkDate()).isNull();
            assertThat(unscheduled.getParkTime()).isNull();
            assertThat(unscheduled.getParkDateTime()).isNull();
            assertThat(unscheduled.getShowAt()).isNull();
        });
    }

    @Test
    @DisplayName("两场时刻相同：按演出编号确定先后，与插入/主键顺序无关")
    void sameParkTimeTieBreaksByCode() {
        persist("S-10", 100L, LocalDateTime.of(2024, 3, 1, 0, 30), "z1", "Z");
        persist("S-03", 1L, LocalDateTime.of(2024, 3, 1, 0, 30), "z2", "Z");
        persist("S-04", 50L, LocalDateTime.of(2024, 3, 1, 0, 30), "z3", "Z");
        bindFindAll();

        assertThat(service.list()).extracting(ShowListItem::getShowCode)
                .containsExactly("S-03", "S-04", "S-10");
    }

    @Test
    @DisplayName("无时刻记录统一置末，内部再按编号排序")
    void nullTimesAllGoLast() {
        persist("S-X9", 9L, null, null, null);
        persist("S-X1", 1L, LocalDateTime.of(2024, 1, 1, 8, 0), "2024-01-01T08:00:00", "BARE");
        persist("S-X5", 5L, null, null, null);
        bindFindAll();

        assertThat(service.list()).extracting(ShowListItem::getShowCode)
                .containsExactly("S-X1", "S-X5", "S-X9");
    }

    @Test
    @DisplayName("建档：不带偏移量的值按园区时间原样解释并记录 BARE")
    void createBareValue() {
        ShowInput in = new ShowInput();
        in.setShowCode("S-C1");
        in.setShowName("新建");
        in.setCapacity(10);
        in.setShowAtRaw("2024-02-29T20:00:00");

        ShowListItem item = service.create(in);
        assertThat(item.getParkDateTime()).isEqualTo("2024-02-29 20:00");
        assertThat(item.getTimeOffset()).isEqualTo("BARE");
        assertThat(item.getTimeSource()).isEqualTo("2024-02-29T20:00:00");
    }

    @Test
    @DisplayName("建档：带偏移量（含夏令时地区写法）的值换算到园区时间并保留偏移标记")
    void createOffsetValue() {
        ShowInput z = new ShowInput();
        z.setShowCode("S-C2");
        z.setShowName("新建2");
        z.setCapacity(10);
        z.setShowAtRaw("2024-02-29T16:30:00Z");
        ShowListItem iz = service.create(z);
        assertThat(iz.getParkDateTime()).isEqualTo("2024-03-01 00:30");
        assertThat(iz.getTimeOffset()).isEqualTo("Z");

        ShowInput dst = new ShowInput();
        dst.setShowCode("S-C3");
        dst.setShowName("新建3");
        dst.setCapacity(10);
        dst.setShowAtRaw("2024-07-15T08:00:00-04:00");
        ShowListItem idst = service.create(dst);
        assertThat(idst.getParkDateTime()).isEqualTo("2024-07-15 20:00");
        assertThat(idst.getTimeOffset()).isEqualTo("-04:00");
    }

    @Test
    @DisplayName("建档：空/空白值表示未排期，字段全部置空且列表置末")
    void createEmptyValueMeansUnscheduled() {
        ShowInput in = new ShowInput();
        in.setShowCode("S-C4");
        in.setShowName("新建4");
        in.setCapacity(10);
        in.setShowAtRaw("   ");
        ShowListItem item = service.create(in);
        assertThat(item.isScheduled()).isFalse();
        assertThat(item.getParkDateTime()).isNull();
        assertThat(item.getTimeSource()).isNull();
        assertThat(item.getTimeOffset()).isNull();
    }

    @Test
    @DisplayName("建档：无法识别的时刻抛中文业务异常")
    void createInvalidValueRejected() {
        ShowInput in = new ShowInput();
        in.setShowCode("S-C5");
        in.setShowName("新建5");
        in.setCapacity(10);
        in.setShowAtRaw("下周六晚上八点");
        assertThatThrownBy(() -> service.create(in))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法识别的演出时刻");
    }
}
