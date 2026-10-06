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

    /**
     * 演出时间，语义固定为园区挂钟时间（Asia/Shanghai）。
     *
     * <p>历史值可能不带偏移量（本身就是园区时间）或带偏移量（已换算到园区时区），
     * 换算规则见 {@link com.amusement.park.common.ParkShowTime}；原始值与偏移量
     * 保留在 {@link #timeSource} / {@link #timeOffset}，解释过程可追溯。
     * 列表按本字段从早到晚排序，null 表示未登记时刻，统一排在末尾。</p>
     */
    @Column(name = "show_at")
    private LocalDateTime showAt;

    /** 原始历史时刻值（不带偏移量 / 带偏移量两种都原样保留），用于追溯 */
    @Column(name = "time_source", length = 64)
    private String timeSource;

    /** BARE=原值无偏移、按园区挂钟原样解释；否则为原值偏移量，如 Z / +04:00 / -05:00 */
    @Column(name = "time_offset", length = 16)
    private String timeOffset;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
