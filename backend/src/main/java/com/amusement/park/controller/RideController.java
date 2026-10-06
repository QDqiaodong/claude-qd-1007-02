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
import com.amusement.park.dto.BoardResult;
import com.amusement.park.entity.Ride;
import com.amusement.park.service.RideService;

/** 游乐设施接口 */
@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    public ApiResponse<List<Ride>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(rideService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Ride> get(@PathVariable Long id) {
        return ApiResponse.success(rideService.get(id));
    }

    @PostMapping
    public ApiResponse<Ride> create(@RequestBody Ride input) {
        return ApiResponse.success("设施已建档", rideService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Ride> update(@PathVariable Long id, @RequestBody Ride input) {
        return ApiResponse.success("已更新", rideService.update(id, input));
    }

    /** 状态流转 */
    @PostMapping("/{id}/transfer")
    public ApiResponse<Ride> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", rideService.transfer(id, target));
    }

    /**
     * 乘坐核验：会员身份 — 卡片有效期 — 设施限制一次完成的可解释结果。
     *
     * <p>memberId 可空：未选会员走散客流程（设施状态 + 身高 + 年龄）；
     * 选择会员时必须实时读取该会员共享主键卡片的有效期，四类条件
     * （有效卡/设施开放/身高达标/年龄达标）全部满足才允许。拒绝时 HTTP 400，
     * data 中携带各项核验明细，message 与首要拒绝原因一致。</p>
     */
    @PostMapping("/{id}/board")
    public ApiResponse<BoardResult> board(@PathVariable Long id,
                                          @RequestParam(required = false) Integer heightCm,
                                          @RequestParam(required = false) Integer age,
                                          @RequestParam(required = false) Long memberId) {
        return ApiResponse.success("核验通过，可以乘坐",
                rideService.board(id, heightCm, age, memberId));
    }
}
