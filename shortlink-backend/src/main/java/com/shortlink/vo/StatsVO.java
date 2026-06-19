package com.shortlink.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 短链接统计VO
 */
@Data
public class StatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 短码
     */
    private String shortCode;

    /**
     * 原始链接
     */
    private String originalUrl;

    /**
     * 总访问量(PV)
     */
    private Long pv;

    /**
     * 独立访客数(UV)
     */
    private Long uv;

    /**
     * 今日访问量
     */
    private Long todayPv;

    /**
     * 今日独立访客
     */
    private Long todayUv;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 访问趋势（按天）
     */
    private List<TrendData> dailyTrend;

    /**
     * 来源分布
     */
    private List<RefererData> refererStats;

    /**
     * 设备分布
     */
    private List<DeviceData> deviceStats;

    /**
     * 最近访问记录
     */
    private List<AccessLogVO> recentAccess;

    @Data
    public static class TrendData implements Serializable {
        private String date;
        private Long pv;
        private Long uv;
    }

    @Data
    public static class RefererData implements Serializable {
        private String referer;
        private Long count;
    }

    @Data
    public static class DeviceData implements Serializable {
        private String device;
        private Long count;
    }
}
