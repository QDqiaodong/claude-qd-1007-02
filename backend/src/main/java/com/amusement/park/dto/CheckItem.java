package com.amusement.park.dto;

/**
 * 乘坐核验中的单项可解释结果：会员身份 / 卡片有效期 / 设施状态 / 身高 / 年龄。
 *
 * <ul>
 *   <li>{@code pass} 该项是否通过；散客（未选会员）时会员与卡片两项为 {@code skipped}；</li>
 *   <li>{@code detail} 该项的核验依据（如“季卡 2026-04-01 ~ 2026-06-30，今天在有效期内”）；</li>
 *   <li>{@code message} 该项未通过时的明确拒绝原因，与页面逐条展示一致。</li>
 * </ul>
 */
public class CheckItem {

    /** 会员身份 / 卡片有效期 / 设施状态 / 身高 / 年龄 */
    private String key;
    private String label;
    private boolean pass;
    /** 散客未选会员时，会员/卡片两项跳过（不参与通过判定） */
    private boolean skipped;
    private String detail;
    private String message;

    public CheckItem() {
    }

    public CheckItem(String key, String label, boolean pass, boolean skipped, String detail, String message) {
        this.key = key;
        this.label = label;
        this.pass = pass;
        this.skipped = skipped;
        this.detail = detail;
        this.message = message;
    }

    public static CheckItem ok(String key, String label, String detail) {
        return new CheckItem(key, label, true, false, detail, null);
    }

    public static CheckItem fail(String key, String label, String detail, String message) {
        return new CheckItem(key, label, false, false, detail, message);
    }

    public static CheckItem skip(String key, String label, String detail) {
        return new CheckItem(key, label, false, true, detail, null);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
