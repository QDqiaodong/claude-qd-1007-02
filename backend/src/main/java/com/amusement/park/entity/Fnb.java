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
 * 餐饮网点：园区内快餐、正餐、饮品等业态。
 */
@Entity
@Table(name = "fnb")
@Getter
@Setter
public class Fnb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 网点编号，唯一 */
    @Column(name = "outlet_code", nullable = false, unique = true, length = 24)
    private String outletCode;

    /** 网点名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 业态：快餐 / 正餐 / 饮品 */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 营业 / 暂停 / 关闭 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
