package com.amusement.park.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ParkShowTime} 纯单测：园区时刻解释规则与可重复性。
 *
 * <p>覆盖：不带偏移量的历史值按上海挂钟原样解释；带偏移量（含 Z、负偏移夏令时、
 * 正偏移）按绝对时刻换算；午夜前后跨日归属；闰日；空时间；同一绝对时刻两种写法
 * 解释一致；格式非法给出中文错误；以及在多个 JVM 默认时区（含夏令时地区）下
 * 结果完全相同——解释绝不依赖系统默认时区。</p>
 */
class ParkShowTimeTest {

    private static final TimeZone ORIGINAL_DEFAULT = TimeZone.getDefault();

    @AfterAll
    static void restoreDefaultZone() {
        TimeZone.setDefault(ORIGINAL_DEFAULT);
    }

    // ---------------- 不带偏移量：园区挂钟原样解释 ----------------

    @ParameterizedTest
    @ValueSource(strings = {"2028-03-03 07:30:00", "2028-03-03T07:30:00", "2028-03-03 07:30"})
    @DisplayName("不带偏移量：一律作为上海挂钟时间，秒可省略、T/空格等价")
    void naiveIsParkWallTime(String raw) {
        assertThat(ParkShowTime.toParkWallTime(raw))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
    }

    @Test
    @DisplayName("不带偏移量：只有日期时补 00:00:00")
    void dateOnlyDefaultsToMidnight() {
        assertThat(ParkShowTime.toParkWallTime("2028-03-03"))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 0, 0, 0));
    }

    // ---------------- 带偏移量：按绝对时刻换算到上海 ----------------

    @Test
    @DisplayName("UTC(Z) 23:30 跨午夜 → 园区日期归到次日 07:30（跨日边界）")
    void utcLateNightRollsToNextParkDay() {
        assertThat(ParkShowTime.toParkWallTime("2028-03-02T23:30:00Z"))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
    }

    @Test
    @DisplayName("UTC(Z) 凌晨 00:30 → 园区日期仍是当天 08:30，不跨日")
    void utcEarlyMorningStaysSameParkDay() {
        assertThat(ParkShowTime.toParkWallTime("2028-03-03T00:30:00Z"))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 8, 30, 0));
    }

    @Test
    @DisplayName("夏令时地区客户端偏移 -07:00：10:00 = 上海次日 01:00（不套用系统 DST）")
    void daylightSavingOffsetConvertedByLiteralOffset() {
        // 洛杉矶 2028-07-15 处于夏令时 UTC-07:00
        assertThat(ParkShowTime.toParkWallTime("2028-07-15T10:00:00-07:00"))
                .isEqualTo(LocalDateTime.of(2028, 7, 16, 1, 0, 0));
    }

    @Test
    @DisplayName("正偏移 +09:00（东京）08:30 = 上海 07:30")
    void positiveOffsetConverted() {
        assertThat(ParkShowTime.toParkWallTime("2028-03-03T08:30:00+09:00"))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
    }

    @Test
    @DisplayName("带偏移量 +08:00 与不带偏移量的同值写法，解释出的园区时刻完全一致")
    void offsetAndNaiveSameInstantAreEqual() {
        LocalDateTime fromOffset = ParkShowTime.toParkWallTime("2028-03-02T23:30:00Z");
        LocalDateTime fromNaive = ParkShowTime.toParkWallTime("2028-03-03 07:30:00");
        assertThat(fromOffset).isEqualTo(fromNaive);
    }

    @Test
    @DisplayName("支持无冒号偏移 2028-03-02T23:30:00+0000")
    void offsetWithoutColon() {
        assertThat(ParkShowTime.toParkWallTime("2028-03-02T23:30:00+0000"))
                .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
    }

    // ---------------- 闰日 ----------------

    @Test
    @DisplayName("闰日：2028-02-29（闰年）正常解释，日期归属准确")
    void leapDayFebruary29() {
        assertThat(ParkShowTime.toParkWallTime("2028-02-29T10:30:00+08:00"))
                .isEqualTo(LocalDateTime.of(2028, 2, 29, 10, 30, 0));
        // 闰日凌晨的 UTC 值仍归到上海 2-29
        assertThat(ParkShowTime.toParkWallTime("2028-02-28T17:00:00Z"))
                .isEqualTo(LocalDateTime.of(2028, 2, 29, 1, 0, 0));
    }

    // ---------------- 空时间与非法值 ----------------

    @Test
    @DisplayName("空时间：null/空串/空白 → null（未排期）")
    void emptyRawMeansUnscheduled() {
        assertThat(ParkShowTime.toParkWallTime(null)).isNull();
        assertThat(ParkShowTime.toParkWallTime("")).isNull();
        assertThat(ParkShowTime.toParkWallTime("   ")).isNull();
    }

    @Test
    @DisplayName("非法字面量：抛中文业务异常，原样回显以便排查")
    void invalidRawRejectedWithChineseMessage() {
        assertThatThrownBy(() -> ParkShowTime.parseRejectable("not-a-date"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("演出时刻格式无法识别")
                .hasMessageContaining("not-a-date");
    }

    // ---------------- 系统默认时区无关性（容器/JVM 环境一致性） ----------------

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Los_Angeles", "Asia/Shanghai", "Pacific/Auckland", "America/Sao_Paulo"})
    @DisplayName("不同 JVM 默认时区（含夏令时地区）下，两类历史值解释结果完全相同")
    void interpretationIsJvmDefaultZoneIndependent(String zoneId) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zoneId)));
        try {
            // 不带偏移量
            assertThat(ParkShowTime.toParkWallTime("2028-03-03 07:30:00"))
                    .as("naive @ %s", zoneId)
                    .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
            // 带偏移量（UTC 跨午夜）
            assertThat(ParkShowTime.toParkWallTime("2028-03-02T23:30:00Z"))
                    .as("offset @ %s", zoneId)
                    .isEqualTo(LocalDateTime.of(2028, 3, 3, 7, 30, 0));
            // 夏令时负偏移
            assertThat(ParkShowTime.toParkWallTime("2028-07-15T10:00:00-07:00"))
                    .as("dst @ %s", zoneId)
                    .isEqualTo(LocalDateTime.of(2028, 7, 16, 1, 0, 0));
            // 闰日
            assertThat(ParkShowTime.toParkWallTime("2028-02-29 10:30:00"))
                    .as("leap @ %s", zoneId)
                    .isEqualTo(LocalDateTime.of(2028, 2, 29, 10, 30, 0));
        } finally {
            TimeZone.setDefault(ORIGINAL_DEFAULT);
        }
    }
}
