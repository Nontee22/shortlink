package com.shortlink.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短链接实体类
 */
@Data
@TableName("t_short_link")
public class ShortLink implements Serializable {

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
     * 原始链接
     */
    private String originalUrl;

    /**
     * URL的SHA-256哈希
     */
    private String urlHash;

    /**
     * 域名
     */
    private String domain;

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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除: 0-未删除, 1-已删除
     */
    @TableLogic
    private Integer deleted;
}

