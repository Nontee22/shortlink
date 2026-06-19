package com.shortlink.service.impl;

import com.shortlink.common.exception.BusinessException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.entity.AccessLog;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.AccessLogMapper;
import com.shortlink.mapper.ShortLinkMapper;
import com.shortlink.service.StatsService;
import com.shortlink.vo.AccessLogVO;
import com.shortlink.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final ShortLinkMapper shortLinkMapper;
    private final AccessLogMapper accessLogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // Redis Key前缀
    private static final String PV_KEY_PREFIX = "shortlink:pv:";
    private static final String UV_KEY_PREFIX = "shortlink:uv:";

    @Override
    public StatsVO getStats(String shortCode) {
        // 查询短链接信息
        ShortLink shortLink = shortLinkMapper.selectByShortCode(shortCode);
        if (shortLink == null) {
            throw new BusinessException(ResultCode.SHORT_LINK_NOT_FOUND);
        }

        StatsVO statsVO = new StatsVO();
        statsVO.setShortCode(shortCode);
        statsVO.setOriginalUrl(shortLink.getOriginalUrl());
        statsVO.setCreatedTime(shortLink.getCreatedTime());

        // 获取PV/UV
        statsVO.setPv(getPv(shortCode));
        statsVO.setUv(getUv(shortCode));

        // 获取今日统计
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        Long todayPv = accessLogMapper.countTodayPv(shortCode, todayStart);
        statsVO.setTodayPv(todayPv != null ? todayPv : 0L);
        
        // 今日UV从HyperLogLog获取
        String todayUvKey = UV_KEY_PREFIX + shortCode + ":" + LocalDate.now();
        Long todayUv = stringRedisTemplate.opsForHyperLogLog().size(todayUvKey);
        statsVO.setTodayUv(todayUv);

        // 获取最近7天访问趋势
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(7);
        List<Map<String, Object>> dailyTrend = accessLogMapper.selectDailyTrend(shortCode, startTime, endTime);
        List<StatsVO.TrendData> trendDataList = new ArrayList<>();
        for (Map<String, Object> item : dailyTrend) {
            StatsVO.TrendData trendData = new StatsVO.TrendData();
            trendData.setDate(item.get("date").toString());
            trendData.setPv(((Number) item.get("pv")).longValue());
            trendData.setUv(((Number) item.get("uv")).longValue());
            trendDataList.add(trendData);
        }
        statsVO.setDailyTrend(trendDataList);

        // 获取来源分布
        List<Map<String, Object>> refererStats = accessLogMapper.selectRefererStats(shortCode);
        List<StatsVO.RefererData> refererDataList = new ArrayList<>();
        for (Map<String, Object> item : refererStats) {
            StatsVO.RefererData refererData = new StatsVO.RefererData();
            refererData.setReferer(item.get("referer").toString());
            refererData.setCount(((Number) item.get("count")).longValue());
            refererDataList.add(refererData);
        }
        statsVO.setRefererStats(refererDataList);

        // 获取设备分布
        List<Map<String, Object>> deviceStats = accessLogMapper.selectDeviceStats(shortCode);
        List<StatsVO.DeviceData> deviceDataList = new ArrayList<>();
        for (Map<String, Object> item : deviceStats) {
            StatsVO.DeviceData deviceData = new StatsVO.DeviceData();
            deviceData.setDevice(item.get("device").toString());
            deviceData.setCount(((Number) item.get("count")).longValue());
            deviceDataList.add(deviceData);
        }
        statsVO.setDeviceStats(deviceDataList);

        // 获取最近访问记录
        List<AccessLog> recentAccess = accessLogMapper.selectRecentAccess(shortCode, 20);
        List<AccessLogVO> accessLogVOList = new ArrayList<>();
        for (AccessLog accessLog : recentAccess) {
            AccessLogVO vo = new AccessLogVO();
            BeanUtils.copyProperties(accessLog, vo);
            accessLogVOList.add(vo);
        }
        statsVO.setRecentAccess(accessLogVOList);

        return statsVO;
    }

    @Override
    public Long getPv(String shortCode) {
        // 优先从Redis获取
        String pvKey = PV_KEY_PREFIX + shortCode;
        String pvStr = stringRedisTemplate.opsForValue().get(pvKey);
        if (pvStr != null) {
            return Long.parseLong(pvStr);
        }
        
        // Redis没有则从数据库 t_short_link.click_count 获取（保持与列表页一致）
        ShortLink shortLink = shortLinkMapper.selectByShortCode(shortCode);
        if (shortLink != null && shortLink.getClickCount() != null) {
            // 同步到Redis
            stringRedisTemplate.opsForValue().set(pvKey, String.valueOf(shortLink.getClickCount()));
            return shortLink.getClickCount();
        }
        
        // 兜底：从访问日志表统计
        Long pv = accessLogMapper.countPvByShortCode(shortCode);
        return pv != null ? pv : 0L;
    }

    @Override
    public Long getUv(String shortCode) {
        // 从HyperLogLog获取总UV
        String uvKey = UV_KEY_PREFIX + shortCode;
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(uvKey);
        
        // 如果Redis没有数据，从数据库统计独立IP数
        if (uv == null || uv == 0) {
            uv = accessLogMapper.countUvByShortCode(shortCode);
        }
        
        return uv != null ? uv : 0L;
    }
}
