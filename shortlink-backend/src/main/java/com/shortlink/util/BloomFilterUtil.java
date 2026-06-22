package com.shortlink.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器工具类
 * 基于 Redisson 实现的分布式布隆过滤器，用于快速判断短码是否已存在
 * <p>
 * 布隆过滤器特性：
 * - 判断"不存在"是绝对准确的（不会漏判）
 * - 判断"存在"可能有误判（误判率由 fpp 参数控制）
 * - 空间效率高，适合海量数据的快速去重场景
 * <p>
 * 应用场景：
 * - 创建短码时快速判断是否重复，减少数据库查询
 * - 查询短码时前置判断，防止缓存穿透
 */
@Slf4j
@Component
public class BloomFilterUtil {

    /**
     * 预计插入元素数量（默认100万）
     * 影响布隆过滤器的位数组大小，值越大占用内存越多，但误判率越稳定
     */
    @Value("${shortlink.bloom-filter.expected-insertions:1000000}")
    private int expectedInsertions;

    /**
     * 期望的误判率（默认0.001，即0.1%）
     * 值越小需要的内存越大，但误判概率越低
     */
    @Value("${shortlink.bloom-filter.fpp:0.001}")
    private double fpp;

    /**
     * Redisson 布隆过滤器实例
     * 基于 Redis 的分布式实现，支持集群环境下的数据一致性
     */
    private RBloomFilter<String> bloomFilter;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 初始化布隆过滤器（Bean创建后自动执行）
     * 使用 tryInit 确保幂等性：若已存在则复用，否则按配置参数创建
     */
    @PostConstruct
    public void init() {
        // 获取或创建名为 "shortlink:bloom" 的布隆过滤器
        bloomFilter = redissonClient.getBloomFilter("shortlink:bloom");
        // 尝试初始化：若过滤器已存在则不会覆盖，保证数据不丢失
        bloomFilter.tryInit(expectedInsertions, fpp);
        log.info("Redisson布隆过滤器初始化完成，预计容量: {}, 误判率: {}, 已有元素数: {}",
                expectedInsertions, fpp, bloomFilter.count());
    }

    /**
     * 添加短码到布隆过滤器
     *
     * @param shortCode 短码
     */
    public void add(String shortCode) {
        bloomFilter.add(shortCode);
    }

    /**
     * 判断短码是否可能存在
     * 返回 false 表示肯定不存在，返回 true 表示可能存在（有误判）
     *
     * @param shortCode 短码
     * @return true=可能存在, false=肯定不存在
     */
    public boolean mightContain(String shortCode) {
        return bloomFilter.contains(shortCode);
    }

    /**
     * 获取布隆过滤器中当前元素数量的近似值
     *
     * @return 近似元素数量
     */
    public long approximateElementCount() {
        return bloomFilter.count();
    }

    /**
     * 获取配置的期望误判率
     *
     * @return 误判率（如 0.001 表示 0.1%）
     */
    public double expectedFpp() {
        return fpp;
    }
}
