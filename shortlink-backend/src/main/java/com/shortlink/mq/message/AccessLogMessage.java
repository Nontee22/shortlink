package com.shortlink.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问日志消息体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 短码
     */
    private String shortCode;

    /**
     * 访问IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 来源页面
     */
    private String referer;

    /**
     * 访问时间
     */
    private LocalDateTime accessTime;
}
