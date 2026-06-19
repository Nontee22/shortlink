package com.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问日志实体类
 */
@Data
@TableName("t_access_log")
public class AccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 短码
     */
    private String shortCode;

    /**
     * 访问IP
     */
    private String ip;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 来源页面
     */
    private String referer;

    /**
     * 设备类型: PC/Mobile/Tablet
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
     * 国家
     */
    private String country;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 访问时间
     */
    private LocalDateTime accessTime;
}
