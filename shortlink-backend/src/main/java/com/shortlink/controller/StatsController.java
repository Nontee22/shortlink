package com.shortlink.controller;

import com.shortlink.common.result.Result;
import com.shortlink.service.StatsService;
import com.shortlink.vo.StatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "统计管理", description = "短链接访问统计相关接口")
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取短链接统计信息
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "获取统计信息", description = "获取短链接的PV、UV、访问趋势等统计数据")
    public Result<StatsVO> getStats(
            @Parameter(description = "短码") @PathVariable String shortCode) {
        StatsVO stats = statsService.getStats(shortCode);
        return Result.success(stats);
    }

    /**
     * 获取PV
     */
    @GetMapping("/{shortCode}/pv")
    @Operation(summary = "获取PV", description = "获取短链接总访问量")
    public Result<Long> getPv(
            @Parameter(description = "短码") @PathVariable String shortCode) {
        Long pv = statsService.getPv(shortCode);
        return Result.success(pv);
    }

    /**
     * 获取UV
     */
    @GetMapping("/{shortCode}/uv")
    @Operation(summary = "获取UV", description = "获取短链接独立访客数")
    public Result<Long> getUv(
            @Parameter(description = "短码") @PathVariable String shortCode) {
        Long uv = statsService.getUv(shortCode);
        return Result.success(uv);
    }
}
