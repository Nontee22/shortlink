package com.shortlink.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 短链接查询DTO
 */
@Data
public class ShortLinkQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关键词（搜索原始URL或描述）
     */
    private String keyword;

    /**
     * 状态: 0-禁用, 1-启用
     */
    private Integer status;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式: asc/desc
     */
    private String sortOrder;

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 10;
}
