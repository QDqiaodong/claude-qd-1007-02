package com.amusement.park.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 演出建档 / 更新入参。
 *
 * <p>{@code showAtRaw} 接收演出时刻的<b>原始字面量</b>，允许两类历史值：
 * 不带偏移量的园区挂钟时间（{@code 2028-03-03 07:30:00}）或带偏移量的
 * 绝对时刻（{@code 2028-03-02T23:30:00Z}）。服务端统一用
 * {@link com.amusement.park.common.ParkShowTime} 解释为上海园区时刻后入库，
 * 前端不做任何时区换算；兼容旧字段名 {@code showAt}。</p>
 */
public class ShowInput {

    /** 演出编号（建档必填） */
    private String showCode;

    /** 演出名称（建档必填） */
    private String showName;

    /** 演出场地 */
    private String venue;

    /** 总容量（建档必填，>0） */
    private Integer capacity;

    /** 已预订座位数 */
    private Integer booked;

    /** 演出时刻原始字面量（可带偏移量，也可不带）；空表示尚未排期 */
    @JsonAlias("showAt")
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
