package com.amusement.park.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 园区演出时刻解释规则的纯单测（不启动 Spring）。
 *
 * <p>覆盖：空时间、不带偏移量的普通日期/跨日/闰日值、带偏移量（Z、±hh:mm、
 * 带区域名含夏令时）的历史值、同一真实瞬间多写法得到同一园区时刻、无法解析的值，
 * 以及 JVM 默认时区被改成 UTC / 美洲夏令时 / 印度时区后结果不变。</p>
 */
class ParkShowTimeTest {

    private TimeZone savedDefault;

    @AfterEach
    void restoreDefaultZone() {
        if (savedDefault != null) {
            TimeZone.setDefault(savedDefault);
            savedDefault = null;
        }
    }

    private void runWithDefaultZone(String zoneId, Runnable body) {
        savedDefault = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
            body.run();
        } finally {
            TimeZone.setDefault(savedDefault);
            savedDefault = null;
        }
    }

    // ---------------- 空时间 ----------------

    @Test
    @DisplayName("空时间：null、空串、空白串都解释为 null（无时刻，列表置末）")
    void emptyTimeMeansUnscheduled() {
        assertThat(ParkShowTime.toParkTime(null)).isNull();
        assertThat(ParkShowTime.toParkTime("")).isNull();
        assertThat(ParkShowTime.toParkTime("   ")).isNull();
        assertThat(ParkShowTime.toStoredText(null)).isNull();
        assertThat(ParkShowTime.parkDate(null)).isNull();
        assertThat(ParkShowTime.parkClock(null)).isNull();
        assertThat(ParkShowTime.parkDateTimeText(null)).isNull();
    }

    // ---------------- 不带偏移量：按园区挂钟原样解释 ----------------

    @Nested
    @DisplayName("不带偏移量的历史值")
    class BareValues {

        @Test
        @DisplayName("普通日期：原样作为园区时间，不平移")
        void ordinaryBareValue() {
            LocalDateTime t = ParkShowTime.toParkTime("2024-02-28T10:00:00");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 2, 28, 10, 0, 0));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2024-02-28");
            assertThat(ParkShowTime.parkClock(t)).isEqualTo("10:00");
            assertThat(ParkShowTime.parkDateTimeText(t)).isEqualTo("2024-02-28 10:00");
        }

        @Test
        @DisplayName("午夜前后：园区 00:30 仍属于当天，不归前一天")
        void afterMidnightBelongsToSameParkDay() {
            LocalDateTime t = ParkShowTime.toParkTime("2026-03-01T00:30:00");
            assertThat(t).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 30));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2026-03-01");
        }

        @Test
        @DisplayName("午夜前：23:59 属于当天")
        void beforeMidnightBelongsToToday() {
            LocalDateTime t = ParkShowTime.toParkTime("2026-03-01T23:59:59");
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2026-03-01");
            assertThat(ParkShowTime.parkClock(t)).isEqualTo("23:59:59");
        }

        @Test
        @DisplayName("闰日：不带偏移量的 2024-02-29 仍是园区闰日")
        void leapDayBareValueStaysOnFeb29() {
            LocalDateTime t = ParkShowTime.toParkTime("2024-02-29T20:00:00");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 2, 29, 20, 0));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2024-02-29");
        }

        @Test
        @DisplayName("兼容空格分隔与缺秒写法")
        void spaceSeparatedAndMissingSeconds() {
            assertThat(ParkShowTime.toParkTime("2026-03-01 00:30"))
                    .isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 30));
            assertThat(ParkShowTime.toParkTime("2026-03-01T00:30"))
                    .isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 30));
        }
    }

    // ---------------- 带偏移量：换算到园区时区 ----------------

    @Nested
    @DisplayName("带偏移量的历史值")
    class OffsetValues {

        @Test
        @DisplayName("UTC 值越过午夜：2026-02-28T16:30Z = 园区 2026-03-01 00:30，归 3 月 1 日")
        void utcCrossesMidnightIntoNextParkDay() {
            LocalDateTime t = ParkShowTime.toParkTime("2026-02-28T16:30:00Z");
            assertThat(t).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 30));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2026-03-01");
            assertThat(ParkShowTime.parkClock(t)).isEqualTo("00:30");
        }

        @Test
        @DisplayName("闰日 UTC 晚场：2024-02-29T16:30Z 换算后已是园区 3 月 1 日，不属闰日")
        void utcLeapDayEveningMovesToMarchFirst() {
            LocalDateTime t = ParkShowTime.toParkTime("2024-02-29T16:30:00Z");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 30));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2024-03-01");
        }

        @Test
        @DisplayName("闰日 UTC 早场：2024-02-29T01:00Z 仍是园区闰日当天")
        void utcLeapDayMorningStaysFeb29() {
            LocalDateTime t = ParkShowTime.toParkTime("2024-02-29T01:00:00Z");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 2, 29, 9, 0));
            assertThat(ParkShowTime.parkDate(t)).isEqualTo("2024-02-29");
        }

        @Test
        @DisplayName("美洲客户端 -05:00 与 UTC 写的同一真实瞬间 → 园区时刻完全相同")
        void negativeOffsetSameInstantEqualsZ() {
            LocalDateTime fromZ = ParkShowTime.toParkTime("2024-02-29T16:30:00Z");
            LocalDateTime fromMinus5 = ParkShowTime.toParkTime("2024-02-29T11:30:00-05:00");
            assertThat(fromMinus5).isEqualTo(fromZ);
            assertThat(fromMinus5).isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 30));
        }

        @Test
        @DisplayName("+04:00 与 Z 写的同一真实瞬间 → 园区时刻完全相同")
        void positiveOffsetSameInstantEqualsZ() {
            LocalDateTime fromZ = ParkShowTime.toParkTime("2024-03-02T11:00:00Z");
            LocalDateTime fromPlus4 = ParkShowTime.toParkTime("2024-03-02T15:00:00+04:00");
            assertThat(fromPlus4).isEqualTo(fromZ);
            assertThat(fromPlus4).isEqualTo(LocalDateTime.of(2024, 3, 2, 19, 0));
        }

        @Test
        @DisplayName("夏令时地区：同瞬间在纽约(-04:00 EDT)与伦敦(+01:00 BST)两种写法 → 园区时刻相同")
        void daylightSavingZonesSameInstant() {
            // 2024-07-15 12:00Z：纽约 UTC-4（夏令时）08:00、伦敦 UTC+1（夏令时）13:00、上海 20:00
            LocalDateTime newYork = ParkShowTime.toParkTime("2024-07-15T08:00:00-04:00");
            LocalDateTime london = ParkShowTime.toParkTime("2024-07-15T13:00:00+01:00");
            LocalDateTime z = ParkShowTime.toParkTime("2024-07-15T12:00:00Z");
            assertThat(newYork).isEqualTo(london).isEqualTo(z);
            assertThat(newYork).isEqualTo(LocalDateTime.of(2024, 7, 15, 20, 0));
            assertThat(ParkShowTime.parkDate(newYork)).isEqualTo("2024-07-15");
        }

        @Test
        @DisplayName("带 [ZoneId] 后缀（含冬令时 -05:00 EST）也能解析并正确跨日")
        void bracketedZoneRegionParsed() {
            // 纽约冬天 2024-02-29 11:30 EST(-05:00) = 16:30Z = 园区 3-01 00:30
            LocalDateTime t = ParkShowTime.toParkTime("2024-02-29T11:30:00-05:00[America/New_York]");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 30));
        }

        @Test
        @DisplayName("带 [ZoneId] 后缀的夏令时写法（EDT -04:00）同样换算")
        void bracketedZoneRegionDaylightTime() {
            LocalDateTime t = ParkShowTime.toParkTime("2024-07-15T08:00:00-04:00[America/New_York]");
            assertThat(t).isEqualTo(LocalDateTime.of(2024, 7, 15, 20, 0));
        }
    }

    // ---------------- 客户端/系统时区无关 ----------------

    @Test
    @DisplayName("系统默认时区分别为 UTC / 美洲夏令时 / 印度 / 上海，解释结果完全一致")
    void resultIndependentOfJvmDefaultZone() {
        String[] zones = {"UTC", "America/New_York", "Asia/Kolkata", "Asia/Shanghai", "Europe/London"};
        for (String zone : zones) {
            runWithDefaultZone(zone, () -> {
                assertThat(ParkShowTime.toParkTime("2024-02-29T20:00:00"))
                        .as("bare value under %s", zone)
                        .isEqualTo(LocalDateTime.of(2024, 2, 29, 20, 0));
                assertThat(ParkShowTime.toParkTime("2024-02-29T16:30:00Z"))
                        .as("Z value under %s", zone)
                        .isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 30));
                assertThat(ParkShowTime.toParkTime("2024-02-29T11:30:00-05:00"))
                        .as("-05:00 value under %s", zone)
                        .isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 30));
            });
        }
    }

    // ---------------- 非法值 ----------------

    @Test
    @DisplayName("无法解析的值抛出 IllegalArgumentException，且提示保留原文")
    void invalidValueRejectedWithRawText() {
        assertThatThrownBy(() -> ParkShowTime.toParkTime("下周六晚上八点"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别的演出时刻")
                .hasMessageContaining("下周六晚上八点");
        assertThatThrownBy(() -> ParkShowTime.toParkTime("2024-02-30T12:00:00"))
                .isInstanceOf(IllegalArgumentException.class);
        // 非法闰日（2023 非闰年）
        assertThatThrownBy(() -> ParkShowTime.toParkTime("2023-02-29T12:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------- 偏移量识别 ----------------

    @Test
    @DisplayName("carriesOffset：Z / ±hh:mm / 带区域名视为带偏移；普通本地时间不视为带偏移")
    void offsetDetection() {
        assertThat(ParkShowTime.carriesOffset("2024-02-29T16:30:00Z")).isTrue();
        assertThat(ParkShowTime.carriesOffset("2024-02-29T11:30:00-05:00")).isTrue();
        assertThat(ParkShowTime.carriesOffset("2024-03-02T15:00:00+04:00")).isTrue();
        assertThat(ParkShowTime.carriesOffset("2024-02-29T11:30:00-05:00[America/New_York]")).isTrue();
        assertThat(ParkShowTime.carriesOffset("2024-02-29T20:00:00")).isFalse();
        assertThat(ParkShowTime.carriesOffset("2024-02-29 20:00")).isFalse();
    }
}
