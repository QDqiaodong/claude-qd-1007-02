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
import com.amusement.park.entity.Show;
import com.amusement.park.service.ShowService;

/** 园区演出接口 */
@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping
    public ApiResponse<List<Show>> list() {
        return ApiResponse.success(showService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Show> get(@PathVariable Long id) {
        return ApiResponse.success(showService.get(id));
    }

    @PostMapping
    public ApiResponse<Show> create(@RequestBody Show input) {
        return ApiResponse.success("演出已建档", showService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Show> update(@PathVariable Long id, @RequestBody Show input) {
        return ApiResponse.success("已更新", showService.update(id, input));
    }

    /** 预订座位：超出容量则拒绝 */
    @PostMapping("/{id}/reserve")
    public ApiResponse<Show> reserve(@PathVariable Long id, @RequestParam Integer seats) {
        return ApiResponse.success("预订成功", showService.reserve(id, seats));
    }
}
