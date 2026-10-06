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
import com.amusement.park.dto.ShowListItem;
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

    /**
     * 列表：已按园区时区（Asia/Shanghai）解释后的真实时刻从早到晚排序，
     * 园区日期/时间由服务端给出，无时刻记录置末。
     */
    @GetMapping
    public ApiResponse<List<ShowListItem>> list() {
        return ApiResponse.success(showService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ShowListItem> get(@PathVariable Long id) {
        return ApiResponse.success(showService.get(id));
    }

    @PostMapping
    public ApiResponse<ShowListItem> create(@RequestBody ShowInput input) {
        return ApiResponse.success("演出已建档", showService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShowListItem> update(@PathVariable Long id, @RequestBody ShowInput input) {
        return ApiResponse.success("已更新", showService.update(id, input));
    }

    /** 预订座位：超出容量则拒绝 */
    @PostMapping("/{id}/reserve")
    public ApiResponse<Show> reserve(@PathVariable Long id, @RequestParam Integer seats) {
        return ApiResponse.success("预订成功", showService.reserve(id, seats));
    }
}
