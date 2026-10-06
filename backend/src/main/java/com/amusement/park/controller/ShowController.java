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
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowScheduleItem;
import com.amusement.park.entity.Show;
import com.amusement.park.service.ShowService;

/**
 * 园区演出接口。
 *
 * <p>列表 {@code GET /api/shows} 返回按园区（上海）真实时刻排序的排期项，
 * 并显式给出园区日期/时间；建档/更新的时刻字面量由服务端统一按时区解释。</p>
 */
@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping
    public ApiResponse<List<ShowScheduleItem>> list() {
        return ApiResponse.success(showService.listSchedule());
    }

    @GetMapping("/{id}")
    public ApiResponse<Show> get(@PathVariable Long id) {
        return ApiResponse.success(showService.get(id));
    }

    @PostMapping
    public ApiResponse<Show> create(@RequestBody ShowInput input) {
        return ApiResponse.success("演出已建档", showService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Show> update(@PathVariable Long id, @RequestBody ShowInput input) {
        return ApiResponse.success("已更新", showService.update(id, input));
    }

    /** 预订座位：超出容量则拒绝 */
    @PostMapping("/{id}/reserve")
    public ApiResponse<Show> reserve(@PathVariable Long id, @RequestParam Integer seats) {
        return ApiResponse.success("预订成功", showService.reserve(id, seats));
    }
}
