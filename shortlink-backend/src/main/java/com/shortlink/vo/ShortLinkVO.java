package com.shortlink.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短链接响应VO
 */
@Data
public class ShortLinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 短码
     */
    private String shortCode;

    /**
     * 短链接完整URL
     */
    private String shortUrl;

    /**
     * 原始链接
     */
    private String originalUrl;

    /**
     * 描述
     */
    private String description;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 状态: 0-禁用, 1-启用
     */
    private Integer status;

    /**
     * 点击次数
     */
    private Long clickCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
