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
 * 游乐设施：过山车、旋转木马、摩天轮等。
 *
 * <p>乘坐前需校验：设施处于「开放」状态、游客身高达到最低身高、年龄不超过上限。</p>
 */
@Entity
@Table(name = "ride")
@Getter
@Setter
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设施编号，唯一 */
    @Column(name = "ride_code", nullable = false, unique = true, length = 24)
    private String rideCode;

    /** 设施名称 */
    @Column(name = "ride_name", nullable = false, length = 64)
    private String rideName;

    /** 最低身高（厘米），null 表示不限 */
    @Column(name = "min_height_cm")
    private Integer minHeightCm;

    /** 最大乘坐年龄，null 表示不限 */
    @Column(name = "max_age")
    private Integer maxAge;

    /** 开放 / 检修 / 关闭 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
