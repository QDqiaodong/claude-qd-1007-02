package com.amusement.park.common;

import java.util.List;
import java.util.Map;

/**
 * 游乐园业务词典与规则参数：游乐设施状态、餐饮业态与状态、会员等级、
 * 年卡/季卡类型，以及各类校验阈值。
 *
 * <p>规则集中在此，便于出题时被要求调整或扩展。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 游乐设施状态：开放 → 检修 → 开放；开放 → 关闭 → 开放 ----------------
    public static final List<String> RIDE_STATUSES = List.of("开放", "检修", "关闭");

    private static final Map<String, String> RIDE_STATUS_NAME = Map.of(
            "开放", "开放",
            "检修", "检修",
            "关闭", "关闭");

    private static final Map<String, List<String>> RIDE_NEXT = Map.of(
            "开放", List.of("检修", "关闭"),
            "检修", List.of("开放"),
            "关闭", List.of("开放"));

    // ---------------- 检修安全复核批次 ----------------
    /** 批次状态：填写中 → 待放行 → 已放行；待放行内容过期 → 已失效（终态，不可放行） */
    public static final List<String> INSPECTION_STATUSES = List.of("填写中", "待放行", "已放行", "已失效");

    /** 必检项：逐项记录，全部「合格」后才允许提交复核 */
    public static final List<String> RIDE_CHECK_ITEMS = List.of("制动系统", "约束装置", "紧急停机", "空载试运行");

    // ---------------- 餐饮业态与状态 ----------------
    public static final List<String> FNB_TYPES = List.of("快餐", "正餐", "饮品");

    private static final Map<String, String> FNB_TYPE_NAME = Map.of(
            "快餐", "快餐",
            "正餐", "正餐",
            "饮品", "饮品");

    public static final List<String> FNB_STATUSES = List.of("营业", "暂停", "关闭");

    private static final Map<String, String> FNB_STATUS_NAME = Map.of(
            "营业", "营业",
            "暂停", "暂停",
            "关闭", "关闭");

    /** 营业 → 暂停 / 关闭；暂停 → 营业 / 关闭；关闭 → 营业 */
    private static final Map<String, List<String>> FNB_NEXT = Map.of(
            "营业", List.of("暂停", "关闭"),
            "暂停", List.of("营业", "关闭"),
            "关闭", List.of("营业"));

    // ---------------- 会员等级 ----------------
    public static final List<String> MEMBER_TIERS = List.of("普通", "银卡", "金卡");

    private static final Map<String, String> MEMBER_TIER_NAME = Map.of(
            "普通", "普通",
            "银卡", "银卡",
            "金卡", "金卡");

    // ---------------- 年卡 / 季卡类型 ----------------
    public static final List<String> PASS_TYPES = List.of("年卡", "季卡");

    private static final Map<String, String> PASS_TYPE_NAME = Map.of(
            "年卡", "年卡",
            "季卡", "季卡");

    // ---------------- 取值方法 ----------------

    public static boolean isValidRideStatus(String status) {
        return status != null && RIDE_STATUS_NAME.containsKey(status);
    }

    public static String rideStatusName(String status) {
        return status == null ? "" : RIDE_STATUS_NAME.getOrDefault(status, status);
    }

    /** 设施状态流转：开放 ↔ 检修，开放 → 关闭 → 开放 */
    public static boolean canTransferRide(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return RIDE_NEXT.getOrDefault(from, List.of()).contains(to);
    }

    public static boolean isValidFnbType(String type) {
        return type != null && FNB_TYPE_NAME.containsKey(type);
    }

    public static String fnbTypeName(String type) {
        return type == null ? "" : FNB_TYPE_NAME.getOrDefault(type, type);
    }

    public static boolean isValidFnbStatus(String status) {
        return status != null && FNB_STATUS_NAME.containsKey(status);
    }

    public static String fnbStatusName(String status) {
        return status == null ? "" : FNB_STATUS_NAME.getOrDefault(status, status);
    }

    /** 餐饮状态流转：营业 ↔ 暂停，营业/暂停 → 关闭，关闭 → 营业 */
    public static boolean canTransferFnb(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return FNB_NEXT.getOrDefault(from, List.of()).contains(to);
    }

    public static boolean isValidMemberTier(String tier) {
        return tier != null && MEMBER_TIER_NAME.containsKey(tier);
    }

    public static String memberTierName(String tier) {
        return tier == null ? "" : MEMBER_TIER_NAME.getOrDefault(tier, tier);
    }

    public static boolean isValidPassType(String type) {
        return type != null && PASS_TYPE_NAME.containsKey(type);
    }

    public static String passTypeName(String type) {
        return type == null ? "" : PASS_TYPE_NAME.getOrDefault(type, type);
    }
}
