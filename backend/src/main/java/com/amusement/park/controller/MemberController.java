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
import com.amusement.park.dto.MemberPassOption;
import com.amusement.park.entity.Member;
import com.amusement.park.service.MemberService;

/** 会员接口 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<Member>> list(@RequestParam(required = false) String tier) {
        return ApiResponse.success(memberService.list(tier));
    }

    /**
     * 乘坐弹窗会员选项：会员 + 共享主键卡片有效期 + 当天卡状态的关联查询，
     * 供设施页乘坐弹窗下拉选择；会员页卡片状态展示也复用同一口径。
     */
    @GetMapping("/boarding-options")
    public ApiResponse<List<MemberPassOption>> boardingOptions(@RequestParam(required = false) String tier) {
        return ApiResponse.success(memberService.listPassOptions(tier));
    }

    @GetMapping("/{id}")
    public ApiResponse<Member> get(@PathVariable Long id) {
        return ApiResponse.success(memberService.get(id));
    }

    @PostMapping
    public ApiResponse<Member> create(@RequestBody Member input) {
        return ApiResponse.success("会员已建档", memberService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Member> update(@PathVariable Long id, @RequestBody Member input) {
        return ApiResponse.success("已更新", memberService.update(id, input));
    }
}
