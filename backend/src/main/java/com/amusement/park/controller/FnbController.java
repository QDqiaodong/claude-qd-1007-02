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
import com.amusement.park.entity.Fnb;
import com.amusement.park.service.FnbService;

/** 餐饮网点接口 */
@RestController
@RequestMapping("/api/fnb")
public class FnbController {

    private final FnbService fnbService;

    public FnbController(FnbService fnbService) {
        this.fnbService = fnbService;
    }

    @GetMapping
    public ApiResponse<List<Fnb>> list(@RequestParam(required = false) String type,
                                       @RequestParam(required = false) String status) {
        return ApiResponse.success(fnbService.list(type, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Fnb> get(@PathVariable Long id) {
        return ApiResponse.success(fnbService.get(id));
    }

    @PostMapping
    public ApiResponse<Fnb> create(@RequestBody Fnb input) {
        return ApiResponse.success("网点已建档", fnbService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Fnb> update(@PathVariable Long id, @RequestBody Fnb input) {
        return ApiResponse.success("已更新", fnbService.update(id, input));
    }

    /** 状态流转 */
    @PostMapping("/{id}/transfer")
    public ApiResponse<Fnb> transfer(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("状态已更新", fnbService.transfer(id, target));
    }
}
