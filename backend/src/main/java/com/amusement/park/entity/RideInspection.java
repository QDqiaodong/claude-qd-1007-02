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
 * 检修安全复核批次：设施进入检修时生成，安全员逐项记录四项必检结果，
 * 全部合格后提交，负责人（不能是提交人）确认后才恢复开放。
 *
 * <p>批次只新增不覆盖：提交后设施限制/状态/复核项发生变化时，本批次被标记
 * 「已失效」并保留在历史中可追溯，新的复核会生成新的批次行。</p>
 */
@Entity
@Table(name = "ride_inspection")
@Getter
@Setter
public class RideInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属设施 */
    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    /** 批次号，唯一，如 INSP-R-003-0007 */
    @Column(name = "batch_no", nullable = false, unique = true, length = 48)
    private String batchNo;

    /** 必检项·制动系统：null=未检，true=合格，false=不合格 */
    @Column(name = "brake_ok")
    private Boolean brakeOk;

    /** 必检项·约束装置 */
    @Column(name = "restraint_ok")
    private Boolean restraintOk;

    /** 必检项·紧急停机 */
    @Column(name = "emergency_stop_ok")
    private Boolean emergencyStopOk;

    /** 必检项·空载试运行 */
    @Column(name = "trial_run_ok")
    private Boolean trialRunOk;

    /** 复核备注 */
    @Column(name = "remark")
    private String remark;

    /** 填写中 / 待放行 / 已放行 / 已失效 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 提交时设施的最低身高快照，用于放行时检测复核内容是否过期 */
    @Column(name = "snapshot_min_height_cm")
    private Integer snapshotMinHeightCm;

    /** 提交时设施的最高年龄快照 */
    @Column(name = "snapshot_max_age")
    private Integer snapshotMaxAge;

    /** 安全员（提交人），提交人不能担任最终放行人 */
    @Column(name = "submitted_by", length = 32)
    private String submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 负责人（最终放行人） */
    @Column(name = "released_by", length = 32)
    private String releasedBy;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    /** 失效原因（限制变更 / 状态变更 / 复核项被修改等） */
    @Column(name = "invalidate_reason", length = 128)
    private String invalidateReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
