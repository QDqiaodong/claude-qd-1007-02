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
 * 园区演出：花车巡游、剧场表演、光影秀等。
 *
 * <p>show 为 MySQL 保留字，表名使用 park_show。预订座位不得超过 capacity。</p>
 */
@Entity
@Table(name = "park_show")
@Getter
@Setter
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 演出编号，唯一 */
    @Column(name = "show_code", nullable = false, unique = true, length = 24)
    private String showCode;

    /** 演出名称 */
    @Column(name = "show_name", nullable = false, length = 64)
    private String showName;

    /** 演出场地 */
    @Column(name = "venue", length = 48)
    private String venue;

    /** 总容量（座位数） */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** 已预订座位数 */
    @Column(name = "booked", nullable = false)
    private Integer booked = 0;

    /** 演出时间 */
    @Column(name = "show_at")
    private LocalDateTime showAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
