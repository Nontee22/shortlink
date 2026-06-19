package com.shortlink.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BloomFilterUtil {

    @Value("${shortlink.bloom-filter.expected-insertions:1000000}")
    private int expectedInsertions;

    @Value("${shortlink.bloom-filter.fpp:0.001}")
    private double fpp;

    private RBloomFilter<String> bloomFilter;

    @Autowired
    private RedissonClient redissonClient;

    @PostConstruct
    public void init() {
        bloomFilter = redissonClient.getBloomFilter("shortlink:bloom");
        bloomFilter.tryInit(expectedInsertions, fpp);
        log.info("Redisson布隆过滤器初始化完成，预计容量: {}, 误判率: {}, 已有元素数: {}",
                expectedInsertions, fpp, bloomFilter.count());
    }

    public void add(String shortCode) {
        bloomFilter.add(shortCode);
    }

    public boolean mightContain(String shortCode) {
        return bloomFilter.contains(shortCode);
    }

    public long approximateElementCount() {
        return bloomFilter.count();
    }

    public double expectedFpp() {
        return fpp;
    }
}
