package com.shortlink.common.interceptor;

import com.shortlink.common.annotation.RateLimit;
import com.shortlink.common.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;

/**
 * 限流拦截器
 * 使用 Redis 滑动窗口算法：ZSET 存储每次请求的时间戳，
 * 每次请求时清除窗口外的旧记录，统计窗口内请求数。
 */
@Slf4j // 日志
@Component // 将普通的 Java 类交给 Spring IOC 容器（工厂） 管理
@RequiredArgsConstructor // 为被 final 修饰的字段，生成构造方法
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：原子化的滑动窗口限流
     * KEYS[1] = 限流 key
     * ARGV[1] = 窗口起始时间（当前时间 - 窗口大小）
     * ARGV[2] = 当前时间（作为 score 和 member）
     * ARGV[3] = 最大请求次数
     * ARGV[4] = 窗口大小（秒），用于设置 key 过期时间
     * 返回: 0=允许, 1=拒绝
     */
    private static final String LUA_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[3]) then
                return 1
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 0
            """;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(LUA_SCRIPT);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    // 在controller执行前执行该拦截接口方法
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // 非 Controller 方法（如静态资源）无需限流，直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        // 方法上没有注解，则不进行限流
        if (rateLimit == null) {
            return true;
        }

        String ip = getClientIp(request); // 获取客户端 IP
        String uri = request.getRequestURI();
        String key = "shortlink:rate:" + ip + ":" + uri;

        long now = System.currentTimeMillis(); // 当前时间
        long windowStart = now - rateLimit.window() * 1000L; // 窗口起始时间

        // 执行 Lua 脚本
        List<String> keys = Collections.singletonList(key);
        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                keys,
                String.valueOf(windowStart),
                String.valueOf(now),
                String.valueOf(rateLimit.maxCount()),
                String.valueOf(rateLimit.window())
        );

        if (result != null && result == 1L) {
            log.warn("IP 限流触发: ip={}, uri={}, window={}s, max={}", ip, uri, rateLimit.window(), rateLimit.maxCount());
            throw new RateLimitException(rateLimit.message());
        }

        return true;
    }

    // 获取客户端 IP 地址
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
