package com.shortlink.task;

import com.shortlink.mapper.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PvSyncTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkMapper shortLinkMapper;

    private static final String PV_KEY_PREFIX = "shortlink:pv:";

    @Scheduled(fixedRate = 300000)
    public void syncPvToDatabase() {
        log.debug("开始同步PV到数据库");
        int count = 0;

        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(PV_KEY_PREFIX + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String shortCode = key.substring(PV_KEY_PREFIX.length());
                String pvStr = stringRedisTemplate.opsForValue().get(key);
                if (pvStr != null) {
                    long pv = Long.parseLong(pvStr);
                    shortLinkMapper.updateClickCount(shortCode, pv);
                    count++;
                }
            }
        }

        if (count > 0) {
            log.info("PV同步完成，共同步{}条记录", count);
        }
    }
}
