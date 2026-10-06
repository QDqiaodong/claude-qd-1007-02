package com.amusement.park.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.amusement.park.dto.ShowScheduleItem;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 演出排期端到端集成测试（H2 内存库 + MockMvc）。
 *
 * <p>覆盖：带偏移量/不带偏移量历史值经 HTTP 写入后归一为上海园区时刻并持久化、
 * 列表按解释后真实时刻排序、相同时刻按编号、空时间沉底、园区日期/时间字段显式输出、
 * 闰日与跨日归属、非法字面量 400、旧字段名 showAt 兼容；并在多个 JVM 默认时区
 * （含夏令时地区）下重复同一组请求，证明展示与排序结果与运行环境时区无关。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShowScheduleIntegrationTest {

    private static final TimeZone ORIGINAL_DEFAULT = TimeZone.getDefault();

    @AfterAll
    static void restoreDefaultZone() {
        TimeZone.setDefault(ORIGINAL_DEFAULT);
    }

    @Autowired private ShowService showService;
    @Autowired private ShowRepository showRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        showRepository.deleteAll();
    }

    private Show persist(String code, String raw, String parkWallTime) {
        Show s = new Show();
        s.setShowCode(code);
        s.setShowName("演出-" + code);
        s.setVenue("测试场地");
        s.setCapacity(300);
        s.setBooked(0);
        s.setShowAtRaw(raw);
        s.setShowAt(parkWallTime == null ? null
                : java.time.LocalDateTime.parse(parkWallTime.replace(' ', 'T')));
        return showRepository.save(s);
    }

    private List<String> orderedCodes() {
        return showService.listSchedule().stream().map(ShowScheduleItem::getShowCode).toList();
    }

    @Test
    @DisplayName("混合历史值列表：按园区时刻排序、相同时刻按编号、空时间沉底")
    void listOrdersInterpretedTimesWithNullsLast() {
        // 仓储按 id/插入顺序返回，刻意乱序插入以证明排序发生在服务层
        persist("S-004", null, "2028-03-04 09:00:00");
        persist("S-011", null, null);                                  // 未排期
        persist("S-009", "2028-03-02T23:30:00Z", "2028-03-03 07:30:00"); // 跨日（UTC）
        persist("S-012", "2028-03-03 07:30:00", "2028-03-03 07:30:00"); // 同一时刻（不带偏移）
        persist("S-008", "2028-02-29T10:30:00+08:00", "2028-02-29 10:30:00"); // 闰日
        persist("S-001", null, "2028-03-03 10:00:00");
        persist("S-010", "2028-07-15T10:00:00-07:00", "2028-07-16 01:00:00"); // 夏令时偏移

        assertThat(orderedCodes())
                .containsExactly("S-008", "S-009", "S-012", "S-001",
                        "S-004", "S-010", "S-011");

        // 显式园区日期/时间：跨日场次归 3 月 3 日
        ShowScheduleItem crossDay = showService.listSchedule().stream()
                .filter(i -> i.getShowCode().equals("S-009")).findFirst().orElseThrow();
        assertThat(crossDay.getParkDate()).isEqualTo("2028-03-03");
        assertThat(crossDay.getParkTime()).isEqualTo("07:30");
        assertThat(crossDay.getParkZone()).isEqualTo("Asia/Shanghai");
        assertThat(crossDay.getShowAtRaw()).isEqualTo("2028-03-02T23:30:00Z");
    }

    @Test
    @DisplayName("HTTP 写入：带 Z 的偏移值换算为上海时刻；列表 JSON 显式给出园区日期/时间")
    void httpCreateOffsetValueInterpreted() throws Exception {
        String body = """
                {"showCode":"H-001","showName":"接口演出","capacity":100,
                 "showAtRaw":"2028-03-02T23:30:00Z"}
                """;
        mockMvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.showAt").value("2028-03-03T07:30:00"))
                .andExpect(jsonPath("$.data.showAtRaw").value("2028-03-02T23:30:00Z"));

        mockMvc.perform(get("/api/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].parkDate").value("2028-03-03"))
                .andExpect(jsonPath("$.data[0].parkTime").value("07:30"))
                .andExpect(jsonPath("$.data[0].parkZone").value("Asia/Shanghai"));
    }

    @Test
    @DisplayName("HTTP 兼容旧字段名 showAt：不带偏移量按上海挂钟原样解释")
    void httpCreateAcceptsLegacyShowAtField() throws Exception {
        String body = """
                {"showCode":"H-002","showName":"旧字段演出","capacity":100,
                 "showAt":"2028-03-03 07:30:00"}
                """;
        mockMvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showAt").value("2028-03-03T07:30:00"))
                .andExpect(jsonPath("$.data.showAtRaw").value("2028-03-03 07:30:00"));
    }

    @Test
    @DisplayName("HTTP：夏令时负偏移 -07:00 换算正确，闰日可正常建档")
    void httpDstOffsetAndLeapDay() throws Exception {
        String dst = """
                {"showCode":"H-003","showName":"夏令时演出","capacity":100,
                 "showAtRaw":"2028-07-15T10:00:00-07:00"}
                """;
        mockMvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(dst))
                .andExpect(jsonPath("$.data.showAt").value("2028-07-16T01:00:00"));

        String leap = """
                {"showCode":"H-004","showName":"闰日演出","capacity":100,
                 "showAtRaw":"2028-02-29 10:30:00"}
                """;
        mockMvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(leap))
                .andExpect(jsonPath("$.data.showAt").value("2028-02-29T10:30:00"));
    }

    @Test
    @DisplayName("HTTP：非法时刻字面量返回 400 中文错误，不落库")
    void httpInvalidRawRejected() throws Exception {
        String body = """
                {"showCode":"H-005","showName":"非法演出","capacity":100,
                 "showAtRaw":"下周六傍晚"}
                """;
        mockMvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("演出时刻格式无法识别")));
        assertThat(showRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("HTTP：更新时刻后排序立即变化，空串清空排期并沉到末尾")
    void httpUpdateReinterpretsAndClears() throws Exception {
        persist("H-100", null, "2028-03-10 09:00:00");
        persist("H-101", null, "2028-03-11 09:00:00");

        // 把 H-101 改到更早（带偏移的跨日值）
        String move = "{\"showAtRaw\":\"2028-03-01T23:30:00Z\"}";
        mockMvc.perform(put("/api/shows/{id}", idOf("H-101"))
                        .contentType(MediaType.APPLICATION_JSON).content(move))
                .andExpect(jsonPath("$.data.showAt").value("2028-03-02T07:30:00"));
        assertThat(orderedCodes()).containsExactly("H-101", "H-100");

        // 清空 H-100 排期 → 沉底
        String clear = "{\"showAtRaw\":\"\"}";
        mockMvc.perform(put("/api/shows/{id}", idOf("H-100"))
                        .contentType(MediaType.APPLICATION_JSON).content(clear))
                .andExpect(jsonPath("$.data.showAt").value(org.hamcrest.Matchers.nullValue()));
        assertThat(orderedCodes()).containsExactly("H-101", "H-100");
    }

    @Test
    @DisplayName("环境一致性：多个 JVM 默认时区（含夏令时）下，同组数据的 JSON 顺序与日期完全相同")
    void sameResultAcrossJvmDefaultZones() throws Exception {
        String[] zones = {"UTC", "America/Los_Angeles", "Asia/Shanghai", "Pacific/Auckland"};
        String expected = null;
        for (String zone : zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            showRepository.deleteAll();
            persist("S-009", "2028-03-02T23:30:00Z", "2028-03-03 07:30:00");
            persist("S-012", "2028-03-03 07:30:00", "2028-03-03 07:30:00");
            persist("S-011", null, null);
            persist("S-010", "2028-07-15T10:00:00-07:00", "2028-07-16 01:00:00");

            MvcResult result = mockMvc.perform(get("/api/shows")).andExpect(status().isOk()).andReturn();
            // 只比较排序编号与园区字段（忽略可能的 JVM 默认时区对 createdAt 序列化的影响）
            List<ShowScheduleItem> items = objectMapper.convertValue(
                    objectMapper.readTree(result.getResponse().getContentAsString()).get("data"),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, ShowScheduleItem.class));
            String fingerprint = items.stream()
                    .map(i -> i.getShowCode() + "|" + i.getParkDate() + "|" + i.getParkTime())
                    .reduce((a, b) -> a + ";" + b).orElse("");
            if (expected == null) {
                expected = fingerprint;
            } else {
                assertThat(fingerprint).as("时区 %s 下结果不一致", zone).isEqualTo(expected);
            }
        }
        // 基准内容断言
        assertThat(expected).isEqualTo(
                "S-009|2028-03-03|07:30;S-012|2028-03-03|07:30;S-010|2028-07-16|01:00;S-011|null|null");
    }

    private Long idOf(String code) {
        return showRepository.findAll().stream()
                .filter(s -> s.getShowCode().equals(code)).findFirst().orElseThrow().getId();
    }
}
