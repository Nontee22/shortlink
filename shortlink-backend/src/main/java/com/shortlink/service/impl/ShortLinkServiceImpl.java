package com.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.common.exception.BusinessException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.dto.ShortLinkCreateDTO;
import com.shortlink.dto.ShortLinkQueryDTO;
import com.shortlink.entity.ShortLink;
import com.shortlink.mapper.ShortLinkMapper;
import com.shortlink.service.ShortLinkService;
import com.shortlink.util.BloomFilterUtil;
import com.shortlink.util.ShortCodeGenerator;
import com.shortlink.vo.ShortLinkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 短链接服务实现类
 */
@Slf4j
@Service
// 使用构造器注入
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortCodeGenerator shortCodeGenerator;
    private final BloomFilterUtil bloomFilterUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Value("${shortlink.domain:http://localhost:8080}")
    private String domain;

    @Value("${shortlink.cache.expire-hours:24}")
    private int expireHours;

    @Value("${shortlink.cache.random-hours:6}")
    private int randomHours;

    // Redis Key前缀
    private static final String CACHE_KEY_PREFIX = "shortlink:url:";
    private static final String LOCK_KEY_PREFIX = "shortlink:lock:";

    private final Random random = new Random();

    @Override
    @Transactional(rollbackFor = Exception.class) // 事务无论遇到什么异常都会回退
    public ShortLinkVO create(ShortLinkCreateDTO dto) {
        String originalUrl = dto.getOriginalUrl();
        String urlHash = sha256(originalUrl);
        // 判断传入的字符串是否“不为空（非空白）”
        boolean isCustom = StringUtils.isNotBlank(dto.getCustomCode());

        // 锁策略：自定义短码按短码维度加锁，自动生成按URL维度加锁
        String lockKey = isCustom
                ? LOCK_KEY_PREFIX + "code:" + dto.getCustomCode()
                : LOCK_KEY_PREFIX + urlHash;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 获取锁，等待5秒，每10毫秒尝试一次
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 自动生成模式下判断是否已经有短码
            if (!isCustom) {
                ShortLink existing = shortLinkMapper.selectByOriginalUrl(urlHash, originalUrl);
                if (existing != null) {
                    log.info("URL已存在短码: {}", existing.getShortCode());
                    return convertToVO(existing);
                }
            }


            String shortCode;
            // 自定义短码
            if (isCustom) {
                shortCode = dto.getCustomCode();
                // 判断短码是否可能存在，返回 true 表示可能存在，false 表示肯定不存在
                if (bloomFilterUtil.mightContain(shortCode)) {
                    ShortLink existByCode = shortLinkMapper.selectByShortCode(shortCode);
                    if (existByCode != null) {
                        throw new BusinessException(ResultCode.SHORT_CODE_EXISTS);
                    }
                }
            } else {
                // 自动生成短码
                do {
                    shortCode = shortCodeGenerator.generate(); // 生成短码
                } while (bloomFilterUtil.mightContain(shortCode));
            }

            // 创建实体
            ShortLink shortLink = new ShortLink();
            shortLink.setShortCode(shortCode);
            shortLink.setOriginalUrl(originalUrl);
            shortLink.setUrlHash(urlHash);
            shortLink.setDomain(domain);
            shortLink.setDescription(dto.getDescription());
            shortLink.setStatus(1);
            shortLink.setClickCount(0L);
            shortLink.setCreatedTime(LocalDateTime.now());
            shortLink.setUpdatedTime(LocalDateTime.now());

            if (dto.getExpireDays() != null && dto.getExpireDays() > 0) {
                shortLink.setExpireTime(LocalDateTime.now().plusDays(dto.getExpireDays()));
            }

            shortLinkMapper.insert(shortLink);
            bloomFilterUtil.add(shortCode);

            int randomExpire = expireHours + random.nextInt(randomHours);
            stringRedisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + shortCode,
                    originalUrl,
                    randomExpire,
                    TimeUnit.HOURS
            );

            log.info("创建短链接成功: {} -> {}{}", shortCode, originalUrl, isCustom ? " (自定义)" : "");
            return convertToVO(shortLink);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 标签设为true，留给上层代码判断，至于停不停，看线程自己的代码怎么写
            throw new BusinessException("创建短链接失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        // 1. 布隆过滤器前置判断（防止缓存穿透）
        if (!bloomFilterUtil.mightContain(shortCode)) {
            log.debug("布隆过滤器判断短码不存在: {}", shortCode);
            return null;
        }
        
        // 2. 查询Redis缓存
        String cacheKey = CACHE_KEY_PREFIX + shortCode;
        String originalUrl = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(originalUrl)) {
            log.debug("缓存命中: {}", shortCode);
            return originalUrl;
        }
        
        // 3. 缓存未命中，查询数据库
        ShortLink shortLink = shortLinkMapper.selectByShortCode(shortCode);
        if (shortLink == null) {
            log.debug("短码不存在: {}", shortCode);
            return null;
        }
        
        // 检查状态
        if (shortLink.getStatus() != 1) {
            throw new BusinessException(ResultCode.SHORT_LINK_DISABLED);
        }
        
        // 检查是否过期
        if (shortLink.getExpireTime() != null && shortLink.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.SHORT_LINK_EXPIRED);
        }
        
        // 4. 回填缓存
        int randomExpire = expireHours + random.nextInt(randomHours);
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                shortLink.getOriginalUrl(),
                randomExpire,
                TimeUnit.HOURS
        );
        
        return shortLink.getOriginalUrl();
    }

    @Override
    public Page<ShortLinkVO> getPage(ShortLinkQueryDTO queryDTO) {
        Page<ShortLink> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        LambdaQueryWrapper<ShortLink> wrapper = new LambdaQueryWrapper<>();
        
        // 关键词搜索
        if (StringUtils.isNotBlank(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(ShortLink::getOriginalUrl, queryDTO.getKeyword())
                    .or().like(ShortLink::getDescription, queryDTO.getKeyword()));
        }
        
        // 状态筛选
        if (queryDTO.getStatus() != null) {
            wrapper.eq(ShortLink::getStatus, queryDTO.getStatus());
        }
        
        // 时间范围筛选
        if (StringUtils.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(ShortLink::getCreatedTime, queryDTO.getStartTime());
        }
        if (StringUtils.isNotBlank(queryDTO.getEndTime())) {
            wrapper.le(ShortLink::getCreatedTime, queryDTO.getEndTime());
        }
        
        // 排序
        if (StringUtils.isNotBlank(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "createdTime" -> wrapper.orderBy(true, isAsc, ShortLink::getCreatedTime);
                case "clickCount" -> wrapper.orderBy(true, isAsc, ShortLink::getClickCount);
                default -> wrapper.orderByDesc(ShortLink::getCreatedTime);
            }
        } else {
            wrapper.orderByDesc(ShortLink::getCreatedTime);
        }
        
        Page<ShortLink> resultPage = shortLinkMapper.selectPage(page, wrapper);
        
        // 转换为VO
        Page<ShortLinkVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).toList());
        
        return voPage;
    }

    @Override
    public ShortLinkVO getByShortCode(String shortCode) {
        ShortLink shortLink = shortLinkMapper.selectByShortCode(shortCode);
        if (shortLink == null) {
            throw new BusinessException(ResultCode.SHORT_LINK_NOT_FOUND);
        }
        return convertToVO(shortLink);
    }

    @Override
    public void updateStatus(String shortCode, Integer status) {
        LambdaUpdateWrapper<ShortLink> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ShortLink::getShortCode, shortCode)
                .set(ShortLink::getStatus, status)
                .set(ShortLink::getUpdatedTime, LocalDateTime.now());
        shortLinkMapper.update(null, wrapper);
        
        // 如果禁用，删除缓存
        if (status == 0) {
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + shortCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String shortCode) {
        // 物理删除：调用自定义Mapper方法直接删除记录
        shortLinkMapper.physicalDeleteByShortCode(shortCode);
        
        // 删除缓存
        stringRedisTemplate.delete(CACHE_KEY_PREFIX + shortCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) {
            return;
        }
        
        // 物理删除：调用自定义Mapper方法批量直接删除记录
        shortLinkMapper.physicalDeleteBatchByShortCodes(shortCodes);
        
        // 批量删除缓存
        List<String> keys = shortCodes.stream()
                .map(code -> CACHE_KEY_PREFIX + code)
                .toList();
        stringRedisTemplate.delete(keys);
    }

    /**
     * 实体转换为VO
     */
    private ShortLinkVO convertToVO(ShortLink entity) {
        ShortLinkVO vo = new ShortLinkVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setShortUrl(domain + "/sl/" + entity.getShortCode());
        return vo;
    }

    /**
     * 对输入字符串进行SHA-256哈希计算
     * 用于URL去重和锁控制，保证相同URL产生相同hash值
     *
     * @param input 待哈希的原始字符串（通常是URL）
     * @return 64位十六进制字符串（256位哈希值的十六进制表示）
     */
    static String sha256(String input) {
        try {
            // 获取SHA-256算法的MessageDigest实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 将输入字符串转为UTF-8字节数组，并计算哈希值（返回32字节数组）
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            // 将字节数组转换为十六进制字符串（JDK 17+ 使用HexFormat）
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256是标准算法，理论上不会抛出此异常，若发生则包装为运行时异常
            throw new RuntimeException(e);
        }
    }
}
