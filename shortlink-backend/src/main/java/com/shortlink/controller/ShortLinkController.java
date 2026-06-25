package com.shortlink.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.common.annotation.RateLimit;
import com.shortlink.common.result.Result;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkQueryDTO;
import com.shortlink.mq.message.AccessLogMessage;
import com.shortlink.mq.producer.AccessLogProducer;
import com.shortlink.service.ShortLinkService;
import com.shortlink.vo.ShortLinkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 短链接控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor // 为final方法提供构造器注入
@Tag(name = "短链接管理", description = "短链接的创建、查询、跳转等接口")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;
    private final AccessLogProducer accessLogProducer;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/create")
    @Operation(summary = "创建短链接", description = "根据原始URL创建短链接") // 描述一个 REST API 操作
    @RateLimit(window = 60, maxCount = 10, message = "创建过于频繁，请1分钟后再试") // 自定义注解，用来限流
    public Result<ShortLinkVO> create(@Valid @RequestBody ShortLinkCreateDTO dto) {
        ShortLinkVO vo = shortLinkService.create(dto);
        return Result.success(vo);
    }

    // 短码正则：1-8位字母数字或连字符，不以连字符开头结尾
    private static final java.util.regex.Pattern SHORT_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9-]{0,6}[A-Za-z0-9])?$");

    /**
     * 短链接跳转
     * PathVariable,从URL路径中获取参数，用于获取短码，例如：/sl/
     */
    @GetMapping("/sl/{shortCode}")
    @Operation(summary = "短链接跳转", description = "访问短链接，302重定向到原始URL")
    public void redirect(
            @Parameter(description = "短码") @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // 校验短码格式（6位字母数字），matcher创建一个匹配器，.matches()是否全匹配
        if (!SHORT_CODE_PATTERN.matcher(shortCode).matches()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "无效的短码");
            return;
        }
        
        // 获取原始URL
        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        
        if (originalUrl == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "短链接不存在");
            return;
        }
        
        // 异步发送访问日志消息
        AccessLogMessage message = AccessLogMessage.builder()
                .shortCode(shortCode)
                .ip(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .accessTime(LocalDateTime.now())
                .build();
        accessLogProducer.send(message);
        
        // 302重定向
        response.sendRedirect(originalUrl);
    }

    /**
     * 分页查询短链接列表
     */
    @GetMapping("/api/short-link/list")
    @Operation(summary = "查询短链接列表", description = "分页查询短链接列表，支持条件筛选和排序")
    public Result<Page<ShortLinkVO>> list(ShortLinkQueryDTO queryDTO) {
        Page<ShortLinkVO> page = shortLinkService.getPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 获取短链接详情
     */
    @GetMapping("/api/short-link/{shortCode}")
    @Operation(summary = "获取短链接详情", description = "根据短码获取短链接详细信息")
    public Result<ShortLinkVO> detail(
            @Parameter(description = "短码") @PathVariable String shortCode) {
        ShortLinkVO vo = shortLinkService.getByShortCode(shortCode);
        return Result.success(vo);
    }

    /**
     * 更新短链接状态
     */
    @PutMapping("/api/short-link/{shortCode}/status")
    @Operation(summary = "更新状态", description = "启用或禁用短链接")
    public Result<Void> updateStatus(
            @Parameter(description = "短码") @PathVariable String shortCode,
            @Parameter(description = "状态: 0-禁用, 1-启用") @RequestParam Integer status) {
        shortLinkService.updateStatus(shortCode, status);
        return Result.success();
    }

    /**
     * 删除短链接
     */
    @DeleteMapping("/api/short-link/{shortCode}")
    @Operation(summary = "删除短链接", description = "根据短码删除短链接")
    public Result<Void> delete(
            @Parameter(description = "短码") @PathVariable String shortCode) {
        shortLinkService.delete(shortCode);
        return Result.success();
    }

    /**
     * 批量删除短链接
     */
    @DeleteMapping("/api/short-link/batch")
    @Operation(summary = "批量删除", description = "批量删除短链接")
    public Result<Void> batchDelete(@RequestBody List<String> shortCodes) {
        shortLinkService.batchDelete(shortCodes);
        return Result.success();
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
