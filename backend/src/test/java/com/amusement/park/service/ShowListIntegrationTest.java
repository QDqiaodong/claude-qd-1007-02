package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.amusement.park.common.BizException;
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowListItem;
import com.amusement.park.repository.ShowRepository;

/**
 * 园区演出列表的园区时区解释与稳定排序集成测试（H2 内存库，Hibernate 建表）。
 *
 * <p>覆盖：空时间置末、相同时刻按演出编号定序、闰日（带/不带偏移量两种归属）、
 * 午夜跨日场次的园区日期归属、带偏移量与不带偏移量的历史值得到一致园区时刻，
 * 以及接口 JSON 直接给出园区日期/时间、空串清空时刻、非法值 400。</p>
 *
 * <p>“多个客户端/系统时区结果一致”由纯单测在不断切换 JVM 默认时区下验证
 * （{@link com.amusement.park.common.ParkShowTimeTest}、{@link ShowServiceTest}，
 * 不经过数据库连接）；本集成测试由 surefire 固定在 UTC 分叉 JVM 中运行
 * （见 pom.xml 的 {@code -Duser.timezone=UTC}），即系统默认时区与园区时区
 * Asia/Shanghai 不同，仍验证解释、归属、排序全部按园区时区得出，保证可重复。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShowListIntegrationTest {

    @Autowired private ShowService showService;
    @Autowired private ShowRepository showRepository;
    @Autowired private MockMvc mockMvc;

    private long seq;

    @BeforeEach
    void setUp() {
        showRepository.deleteAll();
        seq = 0;
    }

    private ShowInput input(String code, String rawTime) {
        ShowInput in = new ShowInput();
        in.setShowCode(code);
        in.setShowName("演出-" + code);
        in.setVenue("测试场地");
        in.setCapacity(100);
        in.setBooked(0);
        in.setShowAtRaw(rawTime);
        return in;
    }

    private ShowInput save(String code, String rawTime) {
        ShowInput in = input(code, rawTime);
        showService.create(in);
        return in;
    }

    /** 插入覆盖全部边界场景的一批演出，返回期望的最终编号顺序（无时刻在末尾）。 */
    private List<String> seedBoundaryShows() {
        save("S-A10", "2024-02-29T20:00:00");                              // 闰日当晚（BARE）
        save("S-A03", "2024-02-29T16:30:00Z");                             // UTC → 3-01 00:30（跨日，不属闰日）
        save("S-A04", "2024-02-29T11:30:00-05:00");                        // -05:00 同瞬间
        save("S-A05", "2024-03-02T15:00:00+04:00");                        // +04:00 → 3-02 19:00
        save("S-A06", "2024-03-02T11:00:00Z");                             // Z 同瞬间（与 A05 相同）
        save("S-A01", "2024-02-28T10:00:00");                              // 普通日期最早
        save("S-A07", null);                                               // 无时刻
        save("S-A08", "");                                                 // 无时刻（空串）
        return List.of("S-A01", "S-A10", "S-A03", "S-A04", "S-A05", "S-A06", "S-A07", "S-A08");
    }

    @Test
    @DisplayName("解释与归属：两类历史值换算后给出正确的园区日期/时间")
    void parkFieldsInterpretedCorrectly() {
        seedBoundaryShows();
        ShowListItem leapBare = byCode("S-A10");
        assertThat(leapBare.isScheduled()).isTrue();
        assertThat(leapBare.getParkDate()).isEqualTo("2024-02-29");
        assertThat(leapBare.getParkTime()).isEqualTo("20:00");
        assertThat(leapBare.getParkDateTime()).isEqualTo("2024-02-29 20:00");
        assertThat(leapBare.getTimeOffset()).isEqualTo("BARE");

        ShowListItem crossDayZ = byCode("S-A03");
        assertThat(crossDayZ.getParkDate()).isEqualTo("2024-03-01");
        assertThat(crossDayZ.getParkTime()).isEqualTo("00:30");
        assertThat(crossDayZ.getShowAt()).isEqualTo("2024-03-01T00:30:00");
        assertThat(crossDayZ.getTimeOffset()).isEqualTo("Z");

        ShowListItem crossDayMinus = byCode("S-A04");
        assertThat(crossDayMinus.getParkDate()).isEqualTo("2024-03-01");
        assertThat(crossDayMinus.getParkDateTime()).isEqualTo("2024-03-01 00:30");
        assertThat(crossDayMinus.getTimeOffset()).isEqualTo("-05:00");

        ShowListItem plus4 = byCode("S-A05");
        assertThat(plus4.getParkDate()).isEqualTo("2024-03-02");
        assertThat(plus4.getParkTime()).isEqualTo("19:00");
        assertThat(plus4.getTimeOffset()).isEqualTo("+04:00");
    }

    @Test
    @DisplayName("排序：按解释后的园区时刻从早到晚；无时刻置末；相同时刻按演出编号定序（可重复）")
    void sortedByParkTimeThenCodeWithNullsLast() {
        List<String> expected = seedBoundaryShows();

        List<ShowListItem> list = showService.list();
        assertThat(list).extracting(ShowListItem::getShowCode).containsExactlyElementsOf(expected);

        // 末尾两条均无时刻，scheduled=false 且日期/时间字段为 null
        assertThat(list).filteredOn(s -> !s.isScheduled()).extracting(ShowListItem::getShowCode)
                .containsExactly("S-A07", "S-A08");
        assertThat(list.get(list.size() - 1).getParkDate()).isNull();
        assertThat(list.get(list.size() - 1).getParkTime()).isNull();

        // 同瞬间两对（S-A03/S-A04 与 S-A05/S-A06）严格按编号先后
        int a03 = indexOf(list, "S-A03");
        int a04 = indexOf(list, "S-A04");
        int a05 = indexOf(list, "S-A05");
        int a06 = indexOf(list, "S-A06");
        assertThat(a03).isLessThan(a04);
        assertThat(a05).isLessThan(a06);
        assertThat(a04).isLessThan(a05);
    }

    @Test
    @DisplayName("可重复性：连续两次列表结果逐字段完全一致")
    void orderIsStableAcrossRepeatedCalls() {
        seedBoundaryShows();
        List<ShowListItem> first = showService.list();
        List<ShowListItem> second = showService.list();
        assertThat(second).usingRecursiveFieldByFieldElementComparator().isEqualTo(first);
    }

    @Test
    @DisplayName("系统默认时区不是园区时区（分叉 JVM 固定 UTC）：园区日期、时间与次序仍按上海时区")
    void parkResultUsedEvenWhenSystemDefaultIsNotShanghai() {
        List<String> expected = seedBoundaryShows();

        // 运行该测试的 JVM 默认时区由 surefire 固定为 UTC（与园区 Asia/Shanghai 不同）
        assertThat(java.util.TimeZone.getDefault().getID()).isEqualTo("UTC");
        List<ShowListItem> list = showService.list();
        assertThat(list).extracting(ShowListItem::getShowCode).containsExactlyElementsOf(expected);
        assertThat(byCode("S-A03").getParkDate()).isEqualTo("2024-03-01");
        assertThat(byCode("S-A03").getParkTime()).isEqualTo("00:30");
        assertThat(byCode("S-A10").getParkDate()).isEqualTo("2024-02-29");
    }

    @Test
    @DisplayName("夏令时客户端写入：同一真实瞬间无论用哪个时区的偏移写法，园区时刻都相同")
    void daylightSavingClientOffsetsNormalizeToSameParkTime() {
        // 2024-07-15 12:00Z：纽约 -04:00（EDT）、伦敦 +01:00（BST）、上海 20:00
        save("S-DST-1", "2024-07-15T08:00:00-04:00");
        save("S-DST-2", "2024-07-15T13:00:00+01:00");
        save("S-DST-3", "2024-07-15T12:00:00Z");
        save("S-DST-4", "2024-07-15T20:00:00"); // 不带偏移量：本身就是园区时间，同属 20:00

        List<ShowListItem> list = showService.list();
        assertThat(list).extracting(ShowListItem::getShowCode)
                .containsExactly("S-DST-1", "S-DST-2", "S-DST-3", "S-DST-4");
        for (ShowListItem item : list) {
            assertThat(item.getParkDateTime()).isEqualTo("2024-07-15 20:00");
        }
    }

    @Test
    @DisplayName("入参校验：无法解析的时刻建档被拒绝且不落库")
    void invalidRawTimeRejected() {
        long before = showRepository.count();
        assertThatThrownBy(() -> showService.create(input("S-BAD", "下周六晚上八点")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法识别的演出时刻");
        assertThat(showRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("更新：空串清空时刻并置末；null 字段保留原时刻")
    void updateClearsOrKeepsShowAt() {
        save("S-UPD", "2024-02-29T16:30:00Z");
        ShowListItem before = byCode("S-UPD");
        assertThat(before.isScheduled()).isTrue();

        ShowInput clear = new ShowInput();
        clear.setShowAtRaw("");
        ShowListItem cleared = showService.update(before.getId(), clear);
        assertThat(cleared.isScheduled()).isFalse();
        assertThat(clearedTimeFieldsNull(cleared)).isTrue();

        // 空串后再次置为带偏移量的值，解释仍正确
        ShowInput reset = new ShowInput();
        reset.setShowAtRaw("2024-02-29T11:30:00-05:00");
        ShowListItem back = showService.update(before.getId(), reset);
        assertThat(back.getParkDateTime()).isEqualTo("2024-03-01 00:30");
    }

    private boolean clearedTimeFieldsNull(ShowListItem item) {
        return item.getShowAt() == null && item.getParkDate() == null && item.getTimeSource() == null;
    }

    @Test
    @DisplayName("HTTP GET /api/shows：返回园区日期/时间与无时刻标志，顺序为园区时刻升序")
    void httpListShapeAndOrder() throws Exception {
        seedBoundaryShows();
        String[] expected = {"S-A01", "S-A10", "S-A03", "S-A04", "S-A05", "S-A06", "S-A07", "S-A08"};
        for (int i = 0; i < expected.length; i++) {
            mockMvc.perform(get("/api/shows"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data[" + i + "].showCode").value(expected[i]));
        }
        // S-A03 由 UTC 值跨日换算
        int idx = indexOf(showService.list(), "S-A03");
        mockMvc.perform(get("/api/shows"))
                .andExpect(jsonPath("$.data[" + idx + "].parkDate").value("2024-03-01"))
                .andExpect(jsonPath("$.data[" + idx + "].parkTime").value("00:30"))
                .andExpect(jsonPath("$.data[" + idx + "].scheduled").value(true))
                .andExpect(jsonPath("$.data[" + idx + "].timeSource").value("2024-02-29T16:30:00Z"))
                .andExpect(jsonPath("$.data[" + idx + "].timeOffset").value("Z"));
        // 末条无时刻
        mockMvc.perform(get("/api/shows"))
                .andExpect(jsonPath("$.data[7].scheduled").value(false))
                .andExpect(jsonPath("$.data[7].parkDate").doesNotExist());
    }

    @Test
    @DisplayName("HTTP POST：带偏移量的值被换算；非法值返回 400 且消息可读")
    void httpCreateOffsetValueAndBadRequest() throws Exception {
        String body = "{\"showCode\":\"S-HTTP-1\",\"showName\":\"接口演出\",\"capacity\":50,"
                + "\"booked\":0,\"showAtRaw\":\"2024-02-29T11:30:00-05:00\"}";
        mockMvc.perform(post("/api/shows").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parkDate").value("2024-03-01"))
                .andExpect(jsonPath("$.data.parkTime").value("00:30"))
                .andExpect(jsonPath("$.data.timeOffset").value("-05:00"));

        String bad = "{\"showCode\":\"S-HTTP-2\",\"showName\":\"坏值\",\"capacity\":50,"
                + "\"showAtRaw\":\"not-a-time\"}";
        mockMvc.perform(post("/api/shows").contentType("application/json").content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("无法识别的演出时刻")));
    }

    @Test
    @DisplayName("HTTP PUT：空串清空时刻")
    void httpUpdateClearsTime() throws Exception {
        ShowListItem created = showService.create(input("S-HTTP-3", "2024-02-29T16:30:00Z"));
        String body = "{\"showAtRaw\":\"\"}";
        mockMvc.perform(put("/api/shows/{id}", created.getId())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduled").value(false))
                .andExpect(jsonPath("$.data.showAt").doesNotExist());
    }

    private ShowListItem byCode(String code) {
        return showService.list().stream()
                .filter(s -> s.getShowCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少演出 " + code));
    }

    private int indexOf(List<ShowListItem> list, String code) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getShowCode().equals(code)) {
                return i;
            }
        }
        throw new AssertionError("缺少演出 " + code);
    }
}
