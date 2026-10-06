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
import com.amusement.park.entity.SeasonPass;
import com.amusement.park.service.SeasonPassService;

/** 年卡 / 季卡接口（与会员共享主键） */
@RestController
@RequestMapping("/api/season-passes")
public class SeasonPassController {

    private final SeasonPassService seasonPassService;

    public SeasonPassController(SeasonPassService seasonPassService) {
        this.seasonPassService = seasonPassService;
    }

    @GetMapping
    public ApiResponse<List<SeasonPass>> list() {
        return ApiResponse.success(seasonPassService.list());
    }

    @GetMapping("/{memberId}")
    public ApiResponse<SeasonPass> getByMember(@PathVariable Long memberId) {
        return ApiResponse.success(seasonPassService.getByMember(memberId));
    }

    /** 为指定会员办卡 */
    @PostMapping
    public ApiResponse<SeasonPass> issue(@RequestParam Long memberId,
                                         @RequestBody SeasonPassService.LocalDateHolder holder) {
        return ApiResponse.success("办卡成功", seasonPassService.issue(memberId, holder));
    }

    /** 续期 / 换卡 */
    @PutMapping("/{memberId}")
    public ApiResponse<SeasonPass> renew(@PathVariable Long memberId,
                                        @RequestBody SeasonPassService.LocalDateHolder holder) {
        return ApiResponse.success("已续期", seasonPassService.renew(memberId, holder));
    }
}
