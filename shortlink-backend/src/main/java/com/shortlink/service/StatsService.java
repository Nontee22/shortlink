package com.shortlink.service;

import com.shortlink.vo.StatsVO;

/**
 * 统计服务接口
 */
public interface StatsService {

    /**
     * 获取短链接统计信息
     * @param shortCode 短码
     * @return 统计信息
     */
    StatsVO getStats(String shortCode);

    /**
     * 获取总访问量
     * @param shortCode 短码
     * @return PV
     */
    Long getPv(String shortCode);

    /**
     * 获取独立访客数
     * @param shortCode 短码
     * @return UV
     */
    Long getUv(String shortCode);
}
