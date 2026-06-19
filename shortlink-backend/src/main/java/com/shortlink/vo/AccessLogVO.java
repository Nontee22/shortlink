package com.shortlink.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问日志VO
 */
@Data
public class AccessLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 来源页面
     */
    private String referer;

    /**
     * 访问时间
     */
    private LocalDateTime accessTime;
}
