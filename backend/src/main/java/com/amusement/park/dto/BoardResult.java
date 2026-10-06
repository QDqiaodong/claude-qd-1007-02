package com.amusement.park.dto;

import java.util.List;

/**
 * 乘坐核验的可解释结果：「会员身份 — 卡片有效期 — 设施限制」一次完成。
 *
 * <p>无论允许还是拒绝都返回本结构（拒绝时 HTTP 400、{@code allowed=false}）。
 * {@link #reason} 是首要拒绝原因，与 {@code checks} 中第一条失败项的
 * {@code message} 完全一致，接口与页面共用这份文案，不会出现两套说法。</p>
 */
public class BoardResult {

    private boolean allowed;
    /** 首要拒绝原因（允许时为“核验通过，可以乘坐”） */
    private String reason;
    private List<CheckItem> checks;

    /** MEMBER（选了会员）/ GUEST（散客） */
    private String visitorType;
    private Long rideId;
    private String rideName;
    private String rideStatus;
    private Long memberId;
    private String memberName;
    private String memberTier;
    private String passType;

    public BoardResult() {
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<CheckItem> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckItem> checks) {
        this.checks = checks;
    }

    public String getVisitorType() {
        return visitorType;
    }

    public void setVisitorType(String visitorType) {
        this.visitorType = visitorType;
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public String getRideName() {
        return rideName;
    }

    public void setRideName(String rideName) {
        this.rideName = rideName;
    }

    public String getRideStatus() {
        return rideStatus;
    }

    public void setRideStatus(String rideStatus) {
        this.rideStatus = rideStatus;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMemberTier() {
        return memberTier;
    }

    public void setMemberTier(String memberTier) {
        this.memberTier = memberTier;
    }

    public String getPassType() {
        return passType;
    }

    public void setPassType(String passType) {
        this.passType = passType;
    }
}
