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
     * 演出时间（统一为园区 Asia/Shanghai 挂钟值，与容器/客户端时区无关）。
     * 由服务端按 {@link com.amusement.park.common.ParkShowTime} 对原始字面量
     * （带或不带偏移量）解释后写入；未排期为 null。
     */
    @Column(name = "show_at")
    private LocalDateTime showAt;

    /**
     * 演出时刻原始字面量（历史可追溯）：保存建档/导入时收到的原文，
     * 可能带偏移量（2028-03-02T23:30:00Z）也可能不带（2028-03-03 07:30:00）；
     * 早期数据没有原文时为 null，其 show_at 按园区挂钟值理解。
     */
    @Column(name = "show_at_raw", length = 40)
    private String showAtRaw;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
