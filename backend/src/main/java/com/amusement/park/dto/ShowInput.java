package com.amusement.park.dto;

/**
 * 演出建档 / 更新入参。
 *
 * <p>演出时刻以字符串 {@link #showAtRaw} 接收，刻意不使用无时区的 LocalDateTime
 * 绑定：历史与客户端数据既有不带偏移量的值（视为园区挂钟时间原样解释），也有
 * 带偏移量的值（按偏移定位瞬间再换算到 Asia/Shanghai）。解释规则见
 * {@link com.amusement.park.common.ParkShowTime}。</p>
 *
 * <p>更新时：字段为 null 表示不修改；空字符串 {@code ""} 表示显式清空时刻；
 * 非空串按上述规则解释。</p>
 */
public class ShowInput {

    private String showCode;
    private String showName;
    private String venue;
    private Integer capacity;
    private Integer booked;

    /** 演出时刻原文；null=不修改（更新时），空串=清空，其余按园区时区规则解释 */
    private String showAtRaw;

    public String getShowCode() {
        return showCode;
    }

    public void setShowCode(String showCode) {
        this.showCode = showCode;
    }

    public String getShowName() {
        return showName;
    }

    public void setShowName(String showName) {
        this.showName = showName;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getBooked() {
        return booked;
    }

    public void setBooked(Integer booked) {
        this.booked = booked;
    }

    public String getShowAtRaw() {
        return showAtRaw;
    }

    public void setShowAtRaw(String showAtRaw) {
        this.showAtRaw = showAtRaw;
    }
}
