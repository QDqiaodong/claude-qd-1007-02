package com.amusement.park.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会员：园区年卡/季卡持有人的基础档案。
 *
 * <p>年卡 {@link SeasonPass} 通过共享主键（@PrimaryKeyJoinColumn）复用本实体主键，
 * 因此本实体不持有对 SeasonPass 的反向引用，避免序列化时的循环引用。</p>
 */
@Entity
@Table(name = "member")
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会员姓名 */
    @Column(name = "name", nullable = false, length = 32)
    private String name;

    /** 会员等级：普通 / 银卡 / 金卡 */
    @Column(name = "tier", nullable = false, length = 16)
    private String tier;

    /** 手机号 */
    @Column(name = "phone", length = 24)
    private String phone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
