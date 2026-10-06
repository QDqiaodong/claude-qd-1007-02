package com.amusement.park.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amusement.park.common.ApiResponse;
import com.amusement.park.entity.RideInspection;
import com.amusement.park.service.RideInspectionService;

/** 检修安全复核与放行接口 */
@RestController
@RequestMapping("/api")
public class RideInspectionController {

    private final RideInspectionService inspectionService;

    public RideInspectionController(RideInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /** 批次历史（含已失效，永久可追溯，不会被新批次覆盖） */
    @GetMapping("/rides/{rideId}/inspections")
    public ApiResponse<List<RideInspection>> history(@PathVariable Long rideId) {
        return ApiResponse.success(inspectionService.history(rideId));
    }

    /** 当前进行中的批次（填写中/待放行），无则 data 为 null */
    @GetMapping("/rides/{rideId}/inspections/active")
    public ApiResponse<RideInspection> active(@PathVariable Long rideId) {
        return ApiResponse.success(inspectionService.active(rideId));
    }

    /** 发起本次复核批次（幂等：已有进行中批次则直接返回，不重复建批） */
    @PostMapping("/rides/{rideId}/inspections")
    public ApiResponse<RideInspection> ensure(@PathVariable Long rideId) {
        return ApiResponse.success("复核批次已就绪", inspectionService.ensureDraft(rideId));
    }

    /** 逐项记录四项必检结果；提交后修改将使原批次失效并生成新批次 */
    @PutMapping("/inspections/{id}/items")
    public ApiResponse<RideInspection> saveItems(@PathVariable Long id, @RequestBody RideInspection input) {
        return ApiResponse.success("复核项已保存", inspectionService.saveItems(id, input));
    }

    /** 提交复核：四项必检全部合格才允许；记录提交人与设施限制快照 */
    @PostMapping("/inspections/{id}/submit")
    public ApiResponse<RideInspection> submit(@PathVariable Long id,
                                              @RequestParam(required = false) String operator) {
        return ApiResponse.success("复核已提交，待负责人放行", inspectionService.submit(id, operator));
    }

    /** 负责人确认恢复开放：提交人不能担任放行人；并发/重复调用只生效一次 */
    @PostMapping("/inspections/{id}/release")
    public ApiResponse<RideInspection> release(@PathVariable Long id,
                                               @RequestParam(required = false) String operator) {
        return ApiResponse.success("复核通过，设施已恢复开放", inspectionService.release(id, operator));
    }
}
