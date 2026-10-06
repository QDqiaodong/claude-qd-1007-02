package com.amusement.park.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 园区时刻解释器：所有演出时刻一律以园区固定时区 {@link ClockConfig#PARK_ZONE}
 * （Asia/Shanghai，全年 UTC+08:00，无夏令时）解释，结果与服务容器、数据库、
 * 浏览器/客户端各自的默认时区无关。
 *
 * <p>历史数据里存在两类字面量，必须得到一致且可追溯的园区时刻：</p>
 * <ul>
 *   <li><b>不带偏移量</b>（如 {@code 2028-03-03 07:30:00}）：语义本身就是
 *       园区墙上时钟（挂钟时间），解释时不查询 JVM/系统默认时区，直接作为
 *       上海本地时间；因此容器跑在 UTC、JVM 默认时区被改动时解释结果不变。</li>
 *   <li><b>带偏移量</b>（如 {@code 2028-03-02T23:30:00Z}、
 *       {@code 2028-07-15T10:00:00-07:00}）：按字面量携带的绝对时刻
 *       （Instant 语义）换算到上海时区，再取上海墙上时间。客户端处于
 *       夏令时地区也只认字面量里的偏移量，不套用任何系统 DST 规则。</li>
 * </ul>
 *
 * <p>同一场演出两种写法只要绝对时刻相同，解释出的园区时刻必然相同，
 * 午夜前后的跨日场次会落到正确的园区日期（如 UTC 23:30 归到上海次日 07:30）。</p>
 */
public final class ParkShowTime {

    /** 园区 UTC 偏移（上海不实行夏令时，固定 +8 小时）。 */
    public static final ZoneOffset PARK_OFFSET = ZoneOffset.ofHours(8);

    /** 园区日期，如 2028-03-03。 */
    public static final DateTimeFormatter PARK_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 园区时间，如 07:30。 */
    public static final DateTimeFormatter PARK_TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** 园区时刻（ISO，本地挂钟值，不带偏移），如 2028-03-03T07:30:00。 */
    public static final DateTimeFormatter PARK_TEXT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 不带偏移量的历史值解析器：日期必填，时间与秒可省略（缺省 00:00:00）。
     * 调用前会把 ISO 分隔符 T 归一化为空格，因此 "2028-03-03T07:30:00" 与
     * "2028-03-03 07:30:00" 等价。
     */
    private static final DateTimeFormatter NAIVE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendPattern(" HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    /**
     * 带偏移量历史值解析器：ISO 日期时间 + 偏移。同时接受 {@code Z}、
     * {@code +08:00}（offsetId）与无冒号的 {@code +0800}（XX），秒可省略。
     */
    private static final DateTimeFormatter OFFSET = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffsetId().optionalEnd()
            .optionalStart().appendPattern("XX").optionalEnd()
            .toFormatter();

    private ParkShowTime() {
    }

    /**
     * 把历史/请求中的演出时刻字面量解释为园区（上海）墙上时间。
     *
     * @param raw 时刻字面量；{@code null} 或空白表示该演出尚未排期
     * @return 园区墙上时间；输入为空时返回 {@code null}
     * @throws IllegalArgumentException 字面量无法按两类受支持格式解析时
     */
    public static LocalDateTime toParkWallTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        // 带偏移量 / Z：按绝对时刻换算到上海。整个解析不依赖系统默认时区。
        if (hasOffset(text)) {
            String iso = text.replace(' ', 'T');
            if (iso.endsWith("z")) {
                iso = iso.substring(0, iso.length() - 1) + "Z";
            }
            return OffsetDateTime.parse(iso, OFFSET)
                    .atZoneSameInstant(ClockConfig.PARK_ZONE).toLocalDateTime();
        }
        // 不带偏移量：历史语义即园区挂钟时间，绝不经过系统默认时区换算。
        return LocalDateTime.parse(text.replace('T', ' '), NAIVE);
    }

    /**
     * 与 {@link #toParkWallTime(String)} 相同，但格式非法时抛出带中文说明的
     * {@link BizException}，供业务入口直接返回 400。
     */
    public static LocalDateTime parseRejectable(String raw) {
        try {
            return toParkWallTime(raw);
        } catch (RuntimeException e) {
            throw new BizException("演出时刻格式无法识别：" + raw
                    + "（支持不带偏移量的园区时间 2028-03-03 07:30:00，或带偏移量的绝对时刻 2028-03-02T23:30:00Z）");
        }
    }

    /**
     * 判断字面量是否携带显式偏移量：以 Z 结尾，或末段带 +HH:mm / -HH:mm（及无冒号形式）。
     */
    private static boolean hasOffset(String text) {
        if (text.endsWith("Z") || text.endsWith("z")) {
            return true;
        }
        int sign = Math.max(text.lastIndexOf('+'), text.lastIndexOf('-'));
        if (sign <= 10) {
            // 位置 <=10 命中的是日期前的负号或日期连字符，并非时间偏移
            return false;
        }
        String tail = text.substring(sign + 1);
        return tail.matches("\\d{2}:?\\d{2}") || tail.matches("\\d{2}");
    }
}
