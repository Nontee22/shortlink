package com.shortlink.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 基于 IP + 接口维度，使用 Redis 滑动窗口实现
 */

// 这个自定义注解只能用在方法
@Target(ElementType.METHOD)
// 这个注解在程序运行时（JVM 运行期间）依然保留
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 时间窗口（秒）
     */
    int window() default 60;

    /**
     * 窗口内最大请求次数
     */
    int maxCount() default 10;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
