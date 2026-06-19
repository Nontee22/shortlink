package com.shortlink.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    
    // 参数错误 4xx
    PARAM_ERROR(400, "参数错误"),
    PARAM_NOT_VALID(401, "参数校验失败"),
    PARAM_NOT_COMPLETE(402, "参数缺失"),
    
    // 业务错误 5xx
    SHORT_LINK_NOT_FOUND(501, "短链接不存在"),
    SHORT_LINK_EXPIRED(502, "短链接已过期"),
    SHORT_LINK_DISABLED(503, "短链接已禁用"),
    URL_INVALID(504, "URL格式不正确"),
    SHORT_CODE_EXISTS(505, "短码已存在"),
    
    // 系统错误 9xx
    SYSTEM_ERROR(900, "系统异常"),
    REDIS_ERROR(901, "Redis服务异常"),
    MQ_ERROR(902, "消息队列服务异常");

    private final Integer code;
    private final String message;
}
