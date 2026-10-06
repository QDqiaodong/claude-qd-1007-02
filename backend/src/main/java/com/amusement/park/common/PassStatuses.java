package com.amusement.park.common;

import java.time.LocalDate;

/**
 * 年卡/季卡有效期判定（单一事实来源）。
 *
 * <p>乘坐核验、会员页展示、接口拒绝原因共用同一套口径：
 * 有效期是<strong>含首尾</strong>的闭区间，只有“今天”满足
 * {@code validFrom <= today <= validTo} 才算「当前有效」。
 * 会员等级（普通/银卡/金卡）与卡片有效性无关，不能用等级代替卡。</p>
 */
public final class PassStatuses {

    /** 当前有效：今天落在 [validFrom, validTo] 闭区间内（含首尾） */
    public static final String ACTIVE = "当前有效";
    /** 未生效：今天早于有效期起 */
    public static final String NOT_STARTED = "未生效";
    /** 已过期：今天晚于有效期止 */
    public static final String EXPIRED = "已过期";

    private PassStatuses() {
    }

    /**
     * 按给定日期判定卡片状态。
     *
     * @param validFrom 有效期起（含）
     * @param validTo   有效期止（含）
     * @param today     判定基准日（通常为园区时区当天）
     */
    public static String classify(LocalDate validFrom, LocalDate validTo, LocalDate today) {
        if (validFrom == null || validTo == null) {
            // 历史脏数据兜底：缺少有效期信息的卡不能当作有效卡
            return EXPIRED;
        }
        if (today.isBefore(validFrom)) {
            return NOT_STARTED;
        }
        if (today.isAfter(validTo)) {
            return EXPIRED;
        }
        return ACTIVE;
    }

    /** 是否在闭区间有效期内（含首尾） */
    public static boolean isValidOn(LocalDate validFrom, LocalDate validTo, LocalDate today) {
        return ACTIVE.equals(classify(validFrom, validTo, today));
    }
}
