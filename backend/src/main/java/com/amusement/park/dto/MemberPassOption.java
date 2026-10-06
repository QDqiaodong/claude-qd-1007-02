package com.amusement.park.dto;

import java.time.LocalDate;

/**
 * 乘坐弹窗会员选项：会员档案 + 共享主键卡片有效期的关联查询结果。
 *
 * <p>{@code passStatus} 由服务层按园区当天日期回填（当前有效/未生效/已过期），
 * 未办卡时卡片字段为 null。会员等级 {@code tier} 仅作展示，不代表有效卡。</p>
 */
public class MemberPassOption {

    private Long memberId;
    private String name;
    private String tier;
    private String phone;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String passType;
    /** 当前有效 / 未生效 / 已过期 / 未办卡 */
    private String passStatus;

    public MemberPassOption() {
    }

    /** JPQL 构造表达式用：不包含 passStatus（由服务层按当天计算） */
    public MemberPassOption(Long memberId, String name, String tier, String phone,
                            LocalDate validFrom, LocalDate validTo, String passType) {
        this.memberId = memberId;
        this.name = name;
        this.tier = tier;
        this.phone = phone;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.passType = passType;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public String getPassType() {
        return passType;
    }

    public void setPassType(String passType) {
        this.passType = passType;
    }

    public String getPassStatus() {
        return passStatus;
    }

    public void setPassStatus(String passStatus) {
        this.passStatus = passStatus;
    }
}
