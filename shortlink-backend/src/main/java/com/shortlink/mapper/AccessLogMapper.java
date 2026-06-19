package com.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.entity.AccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 访问日志Mapper
 */
@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    /**
     * 统计指定短码的访问次数(PV)
     */
    Long countPvByShortCode(@Param("shortCode") String shortCode);

    /**
     * 统计指定短码的独立访客数(UV)
     */
    Long countUvByShortCode(@Param("shortCode") String shortCode);

    /**
     * 按天统计访问趋势
     */
    List<Map<String, Object>> selectDailyTrend(@Param("shortCode") String shortCode,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 统计来源分布
     */
    List<Map<String, Object>> selectRefererStats(@Param("shortCode") String shortCode);

    /**
     * 统计设备分布
     */
    List<Map<String, Object>> selectDeviceStats(@Param("shortCode") String shortCode);

    /**
     * 查询最近访问记录
     */
    List<AccessLog> selectRecentAccess(@Param("shortCode") String shortCode, @Param("limit") int limit);

    /**
     * 统计今日PV
     */
    Long countTodayPv(@Param("shortCode") String shortCode, @Param("today") LocalDateTime today);
}
