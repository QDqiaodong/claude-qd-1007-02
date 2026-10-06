package com.amusement.park.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * 园区演出时刻的统一解释规则：所有历史值一律按园区固定时区
 * （{@link ClockConfig#PARK_ZONE}，Asia/Shanghai，UTC+08:00，无夏令时）换算后解释。
 *
 * <p>历史数据存在两类字符串，解释方式都确定且可追溯：</p>
 * <ul>
 *   <li><b>不带偏移量</b>（如 {@code 2026-03-01T00:30:00}）：该串本身就是园区
 *       本地挂钟时间，原样作为园区时刻，不做任何时区平移（即不依赖容器/JVM 默认时区）；</li>
 *   <li><b>带偏移量或时区</b>（如 {@code 2026-02-28T16:30:00Z}、
 *       {@code 2026-02-28T11:30:00-05:00}、{@code 2024-07-15T08:00:00-04:00[America/New_York]}）：
 *       按其携带的偏移量定位真实瞬间，再换算到园区时区。不同客户端（含夏令时地区）
 *       传入同一真实瞬间时，得到的园区时刻必然相同。</li>
 * </ul>
 *
 * <p>换算结果决定“这场演出属于园区哪一天”，因此午夜前后的跨日场次按园区日历归属
 * （例：UTC 2026-02-28T16:30:00Z = 园区 2026-03-01 00:30，属于 3 月 1 日）；
 * 闰日按园区日期判断（例：UTC 2024-02-29T16:30:00Z = 园区 2024-03-01 00:30，
 * 不再属于 2 月 29 日；而不带偏移量的 2024-02-29T20:00 仍是园区闰日当晚）。</p>
 *
 * <p>本类只依赖显式的园区 ZoneId，不读取系统默认时区，JVM/容器时区变化、
 * 客户端时区或夏令时规则都不会改变解释结果。</p>
 */
public final class ParkShowTime {

    /** 本地日期时间 + 偏移量（Z / ±hh:mm），末尾可选 [ZoneId] 区域名后缀；严格解析，非法日期（如非闰年 2-29）直接拒绝 */
    private static final DateTimeFormatter OFFSET_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffsetId()
            .appendOptional(new DateTimeFormatterBuilder()
                    .appendLiteral('[')
                    .parseCaseSensitive()
                    .appendZoneRegionId()
                    .appendLiteral(']')
                    .toFormatter())
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    /** 不带偏移量的园区挂钟时间；严格解析，非法日期直接拒绝 */
    private static final DateTimeFormatter BARE_PARSER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    /** 列表展示用：园区日期（ISO）、园区时间（整点/整分不显示秒）、完整园区时刻 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_MINUTES = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ParkShowTime() {
    }

    /**
     * 把历史/入参字符串解释为园区挂钟时间（LocalDateTime 的语义固定为 Asia/Shanghai）。
     *
     * @param raw 原始值；null 或空白表示该演出没有登记时刻
     * @return 园区时刻；无时刻返回 null
     * @throws IllegalArgumentException 值非空但无法按两种形式解析时抛出，保留原文以便追溯
     */
    public static LocalDateTime toParkTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            if (carriesOffset(text)) {
                // 先按“本地时刻 + 偏移量”解析为绝对瞬间（OffsetDateTime 对 Z 与 ±hh:mm 都成立），
                // 再统一换算到园区时区；带 [ZoneId] 后缀的串退化为 ZonedDateTime 解析。
                OffsetDateTime offset;
                try {
                    offset = OffsetDateTime.parse(text, OFFSET_PARSER);
                } catch (DateTimeParseException inner) {
                    offset = ZonedDateTime.parse(text).toOffsetDateTime();
                }
                return offset.atZoneSameInstant(ClockConfig.PARK_ZONE).toLocalDateTime();
            }
            // 不带偏移量：串本身就是园区挂钟时间。容忍 "yyyy-MM-dd HH:mm"（空格分隔）与缺秒。
            String iso = text.replace(' ', 'T');
            return LocalDateTime.parse(iso, BARE_PARSER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("无法识别的演出时刻：" + raw
                    + "（应为不带偏移量的园区时间，或带时区偏移量的时间，如 2026-03-01T00:30:00 / 2026-02-28T16:30:00Z）");
        }
    }

    /**
     * 园区时刻重新规范化为标准串（用于写入/回显，与存储的 LocalDateTime 语义一致）。
     */
    public static String toStoredText(LocalDateTime parkTime) {
        return parkTime == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(parkTime);
    }

    /**
     * 从原始串提取规范化偏移量用于追溯：UTC 返回 {@code Z}，其余返回 {@code ±hh:mm}。
     * 调用前应已通过 {@link #carriesOffset} 判定为带偏移量且 {@link #toParkTime} 可解析。
     */
    public static String normalizedOffset(String raw) {
        String text = raw.trim().replace(' ', 'T');
        OffsetDateTime offset;
        try {
            offset = OffsetDateTime.parse(text, OFFSET_PARSER);
        } catch (DateTimeParseException e) {
            offset = ZonedDateTime.parse(text).toOffsetDateTime();
        }
        ZoneOffset zoneOffset = offset.getOffset();
        return zoneOffset.equals(ZoneOffset.UTC) ? "Z" : zoneOffset.getId();
    }

    /** 园区日期，如 {@code 2026-03-01}；无时刻返回 null。 */
    public static String parkDate(LocalDateTime parkTime) {
        return parkTime == null ? null : DATE_FORMATTER.format(parkTime);
    }

    /** 园区时间，整分场次不显示秒，如 {@code 00:30} / {@code 20:00:30}；无时刻返回 null。 */
    public static String parkClock(LocalDateTime parkTime) {
        if (parkTime == null) {
            return null;
        }
        return (parkTime.getSecond() == 0 && parkTime.get(ChronoField.NANO_OF_SECOND) == 0)
                ? TIME_MINUTES.format(parkTime) : TIME_WITH_SECONDS.format(parkTime);
    }

    /** 完整园区时刻（日期 + 时间），整分场次不显示秒；无时刻返回 null。 */
    public static String parkDateTimeText(LocalDateTime parkTime) {
        if (parkTime == null) {
            return null;
        }
        return (parkTime.getSecond() == 0 && parkTime.get(ChronoField.NANO_OF_SECOND) == 0)
                ? DATETIME_MINUTES.format(parkTime) : DATETIME_WITH_SECONDS.format(parkTime);
    }

    /**
     * 判定原始串是否携带时区偏移量信息。
     *
     * <p>ISO-8601 结构为 {@code 日期 T 时间 偏移量 [区域名]}，日期中的 {@code -} 一定出现在
     * 第一个 T/空格之前。因此取时间部分（首个 T/空格之后）：出现结尾 Z，或出现 {@code ±hh:mm}
     * （冒号可缺省），即视为带偏移量；带 {@code [区域名]} 后缀时必然同时带偏移量。</p>
     */
    public static boolean carriesOffset(String text) {
        int split = Math.max(text.indexOf('T'), text.indexOf('t'));
        int space = text.indexOf(' ');
        if (space >= 0 && (split < 0 || space < split)) {
            split = space;
        }
        String timePart = split >= 0 ? text.substring(split + 1) : text;
        if (timePart.endsWith("Z") || timePart.endsWith("z")) {
            return true;
        }
        for (int i = 0; i < timePart.length(); i++) {
            char c = timePart.charAt(i);
            if (c == '+' || c == '-') {
                // 偏移量形如 hh:mm 或 hhmm，至少两位小时数字
                if (i + 2 < timePart.length()
                        && Character.isDigit(timePart.charAt(i + 1))
                        && Character.isDigit(timePart.charAt(i + 2))) {
                    return true;
                }
            }
        }
        return false;
    }
}
