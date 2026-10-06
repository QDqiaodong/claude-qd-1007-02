package com.amusement.park.dto;

import com.amusement.park.common.ParkShowTime;
import com.amusement.park.entity.Show;

/**
 * 演出列表项：在演出档案基础上，显式呈现按园区固定时区（Asia/Shanghai）
 * 解释后的园区日期与时间，列表即按该真实时刻排序。
 *
 * <p>页面只渲染这里给出的字符串字段，不在浏览器里做任何 {@code new Date()}
 * 换算，因此客户端处于夏令时地区、系统时区变化或前后端默认时区不一致时，
 * 看到的园区日期/时间与先后顺序都完全相同。</p>
 *
 * <ul>
 *   <li>{@code parkDate}：园区日期 {@code yyyy-MM-dd}（午夜前后跨日场次归到上海日期）；</li>
 *   <li>{@code parkTime}：园区时间 {@code HH:mm}；</li>
 *   <li>{@code showAt}：完整园区时刻 ISO 文本（挂钟值，不带偏移）；</li>
 *   <li>{@code parkZone}：固定 {@code Asia/Shanghai}，{@code parkZoneLabel} 为展示标签；</li>
 *   <li>{@code showAtRaw}：入库时的原始字面量（可追溯，允许为 null）。</li>
 * </ul>
 */
public class ShowScheduleItem {

    private Long id;
    private String showCode;
    private String showName;
    private String venue;
    private Integer capacity;
    private Integer booked;

    /** 完整园区时刻（上海挂钟值）；未排期为 null */
    private String showAt;
    /** 园区日期 yyyy-MM-dd；未排期为 null */
    private String parkDate;
    /** 园区时间 HH:mm；未排期为 null */
    private String parkTime;
    /** 园区时区标识，固定 Asia/Shanghai */
    private String parkZone;
    /** 园区时区展示标签，固定 UTC+08:00（上海） */
    private String parkZoneLabel;
    /** 历史原始字面量，用于追溯；旧数据可能为 null */
    private String showAtRaw;

    public ShowScheduleItem() {
    }

    /**
     * 由实体投影。{@link Show#getShowAt()} 已经是解释后的上海挂钟值，
     * 这里仅做与系统时区无关的字符串格式化。
     */
    public static ShowScheduleItem of(Show show) {
        ShowScheduleItem item = new ShowScheduleItem();
        item.id = show.getId();
        item.showCode = show.getShowCode();
        item.showName = show.getShowName();
        item.venue = show.getVenue();
        item.capacity = show.getCapacity();
        item.booked = show.getBooked();
        item.showAtRaw = show.getShowAtRaw();
        item.parkZone = "Asia/Shanghai";
        item.parkZoneLabel = "UTC+08:00（上海）";
        if (show.getShowAt() != null) {
            item.showAt = ParkShowTime.PARK_TEXT.format(show.getShowAt());
            item.parkDate = ParkShowTime.PARK_DATE.format(show.getShowAt());
            item.parkTime = ParkShowTime.PARK_TIME.format(show.getShowAt());
        }
        return item;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getShowAt() {
        return showAt;
    }

    public void setShowAt(String showAt) {
        this.showAt = showAt;
    }

    public String getParkDate() {
        return parkDate;
    }

    public void setParkDate(String parkDate) {
        this.parkDate = parkDate;
    }

    public String getParkTime() {
        return parkTime;
    }

    public void setParkTime(String parkTime) {
        this.parkTime = parkTime;
    }

    public String getParkZone() {
        return parkZone;
    }

    public void setParkZone(String parkZone) {
        this.parkZone = parkZone;
    }

    public String getParkZoneLabel() {
        return parkZoneLabel;
    }

    public void setParkZoneLabel(String parkZoneLabel) {
        this.parkZoneLabel = parkZoneLabel;
    }

    public String getShowAtRaw() {
        return showAtRaw;
    }

    public void setShowAtRaw(String showAtRaw) {
        this.showAtRaw = showAtRaw;
    }
}
