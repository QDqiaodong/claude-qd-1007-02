package com.amusement.park.dto;

/**
 * 园区演出列表项。
 *
 * <p>列表不再直接吐出无时区含义的 {@code showAt}，而是给出已经按园区时区
 * （Asia/Shanghai）解释后的字段，页面直接渲染即可，无需（也不应）在客户端
 * 再用浏览器时区做任何换算：</p>
 * <ul>
 *   <li>{@link #parkDate} 园区日期（yyyy-MM-dd），决定该场属于园区哪一天；</li>
 *   <li>{@link #parkTime} 园区时间（HH:mm，带秒场次显示秒）；</li>
 *   <li>{@link #parkDateTime} 完整园区时刻，列表按它从早到晚排序；</li>
 *   <li>{@link #showAt} 解释后的园区时刻（ISO，无时区后缀，语义固定为上海时间），
 *       供编辑回显与排序核对；</li>
 *   <li>{@link #scheduled} 是否登记了演出时刻，false 的记录统一排在末尾；</li>
 *   <li>{@link #timeSource} 原始历史值；{@link #timeOffset} 为 {@code BARE}
 *       （原值无偏移，按园区挂钟原样解释）或具体偏移量（原值带偏移，已据此换算），
 *       两类历史值的解释过程都可追溯。</li>
 * </ul>
 */
public class ShowListItem {

    private Long id;
    private String showCode;
    private String showName;
    private String venue;
    private Integer capacity;
    private Integer booked;

    private boolean scheduled;
    private String parkDate;
    private String parkTime;
    private String parkDateTime;
    private String showAt;

    /** 原始历史值（不带偏移量 / 带偏移量两种） */
    private String timeSource;
    /** BARE=原值无偏移按园区时间解释；否则为原值偏移量，如 Z、+04:00、-05:00 */
    private String timeOffset;

    public ShowListItem() {
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

    public boolean isScheduled() {
        return scheduled;
    }

    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
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

    public String getParkDateTime() {
        return parkDateTime;
    }

    public void setParkDateTime(String parkDateTime) {
        this.parkDateTime = parkDateTime;
    }

    public String getShowAt() {
        return showAt;
    }

    public void setShowAt(String showAt) {
        this.showAt = showAt;
    }

    public String getTimeSource() {
        return timeSource;
    }

    public void setTimeSource(String timeSource) {
        this.timeSource = timeSource;
    }

    public String getTimeOffset() {
        return timeOffset;
    }

    public void setTimeOffset(String timeOffset) {
        this.timeOffset = timeOffset;
    }
}
