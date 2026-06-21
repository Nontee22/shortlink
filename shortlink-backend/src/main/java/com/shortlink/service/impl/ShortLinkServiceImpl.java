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
    // 重写父类/接口方法，标识这是一个业务方法
    @Transactional(rollbackFor = Exception.class)
    // 声明式事务管理：任何异常（Exception及其子类）发生时都会回滚事务，保证数据一致性
    public ShortLinkVO create(ShortLinkCreateDTO dto) {
        // 获取原始URL
        String originalUrl = dto.getOriginalUrl();
        // 对原始URL进行SHA256哈希处理，用于URL去重和锁控制
        // SHA256保证相同URL产生相同hash值，且不可逆，长度固定（64位十六进制）
        String urlHash = sha256(originalUrl);
        // 判断传入的字符串是否"不为空（非空白）"
        // StringUtils.isNotBlank() 会检查字符串非null、非空字符串、且不全为空白字符
        // 用于区分是用户自定义短码还是系统自动生成短码
        boolean isCustom = StringUtils.isNotBlank(dto.getCustomCode());

        // 锁策略：自定义短码按短码维度加锁，自动生成按URL维度加锁
        // 锁粒度设计：自定义场景锁短码本身（防止并发创建相同自定义码），
        // 自动生成场景锁URL哈希（防止同一URL并发创建多个短码，实现去重）
        String lockKey = isCustom
                ? LOCK_KEY_PREFIX + "code:" + dto.getCustomCode()  // 自定义模式：锁具体短码
                : LOCK_KEY_PREFIX + urlHash;  // 自动模式：锁URL哈希
        // 获取Redisson分布式锁对象（基于Redis实现）
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取分布式锁，支持超时机制
            // 参数：等待时间5秒（获取锁的最大等待时间），持有时间10秒（锁自动释放时间），时间单位秒
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                // 获取锁失败，说明有其他线程正在处理相同资源，抛出业务异常
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // ========== 自动生成模式：同URL去重检查 ==========
            if (!isCustom) {
                // 通过URL哈希+原始URL精确查询，判断是否已存在相同URL的短码
                // 使用双重条件（哈希+原始URL）防止哈希碰撞导致的误判
                ShortLink existing = shortLinkMapper.selectByOriginalUrl(urlHash, originalUrl);
                if (existing != null) {
                    // 如果已存在，直接返回已存在的短链接信息，实现去重复用
                    log.info("URL已存在短码: {}", existing.getShortCode());
                    return convertToVO(existing);
                }
            }

            // ========== 确定短码 ==========
            String shortCode;
            if (isCustom) {
                // 自定义模式：直接使用用户传入的自定义短码
                shortCode = dto.getCustomCode();
                // 唯一性校验：布隆过滤器 + DB双重确认
                // 布隆过滤器快速判断（可能存在误判，但不会漏判）
                if (bloomFilterUtil.mightContain(shortCode)) {
                    // 如果布隆过滤器认为可能存在，再查询数据库确认（双重校验）
                    ShortLink existByCode = shortLinkMapper.selectByShortCode(shortCode);
                    if (existByCode != null) {
                        // 数据库中确实存在，说明短码已被占用，抛出异常
                        throw new BusinessException(ResultCode.SHORT_CODE_EXISTS);
                    }
                }
                // 如果布隆过滤器认为不存在，说明短码肯定可用（布隆过滤器不会漏判）
            } else {
                // 自动生成模式：调用短码生成器生成新短码
                shortCode = shortCodeGenerator.generate();
                // 循环检查：如果布隆过滤器认为已存在，则重新生成
                // 使用布隆过滤器过滤掉大部分重复，减少数据库查询
                while (bloomFilterUtil.mightContain(shortCode)) {
                    shortCode = shortCodeGenerator.generate();
                }
            }

            // ========== 创建实体对象 ==========
            ShortLink shortLink = new ShortLink();
            shortLink.setShortCode(shortCode);  // 设置短码（唯一标识）
            shortLink.setOriginalUrl(originalUrl);  // 设置原始URL
            shortLink.setUrlHash(urlHash);  // 设置URL哈希（用于快速查询）
            shortLink.setDomain(domain);  // 设置域名（如：short.cn）
            shortLink.setDescription(dto.getDescription());  // 设置描述信息
            shortLink.setStatus(1);  // 状态：1-正常 0-禁用
            shortLink.setClickCount(0L);  // 初始点击次数为0
            shortLink.setCreatedTime(LocalDateTime.now());  // 创建时间
            shortLink.setUpdatedTime(LocalDateTime.now());  // 更新时间

            // 如果指定了过期天数且大于0，计算过期时间
            if (dto.getExpireDays() != null && dto.getExpireDays() > 0) {
                shortLink.setExpireTime(LocalDateTime.now().plusDays(dto.getExpireDays()));
            }

            // ========== 保存到数据库 ==========
            shortLinkMapper.insert(shortLink);
            // 将短码添加到布隆过滤器，后续查询可快速判断是否存在
            bloomFilterUtil.add(shortCode);

            // ========== 写入缓存（热点数据加速访问） ==========
            // 设置随机过期时间（基础时间 + 随机时间），防止缓存雪崩
            // 即大量缓存同时过期导致请求全部打到数据库
            int randomExpire = expireHours + random.nextInt(randomHours);
            stringRedisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + shortCode,  // Redis Key: 如 "short:abc123"
                    originalUrl,  // Redis Value: 原始URL
                    randomExpire,  // 过期时间（小时）
                    TimeUnit.HOURS
            );

            // 记录成功日志，区分是否自定义
            log.info("创建短链接成功: {} -> {}{}", shortCode, originalUrl, isCustom ? " (自定义)" : "");
            // 将实体对象转换为VO（View Object）返回给前端
            return convertToVO(shortLink);

        } catch (InterruptedException e) {
            // 处理线程中断异常：在等待锁时被中断
            // 恢复中断状态，让上层调用者知道线程被中断
            Thread.currentThread().interrupt();
            throw new BusinessException("创建短链接失败");
        } finally {
            // 释放锁：确保只有当前线程持有的锁才会被释放
            // 使用 isHeldByCurrentThread() 防止解别人持有的锁
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
        LambdaUpdateWrapper<ShortLink> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ShortLink::getShortCode, shortCode)
                .set(ShortLink::getDeleted, 1)
                .set(ShortLink::getUpdatedTime, LocalDateTime.now());
        shortLinkMapper.update(null, wrapper);
        
        // 删除缓存
        stringRedisTemplate.delete(CACHE_KEY_PREFIX + shortCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) {
            return;
        }
        
        LambdaUpdateWrapper<ShortLink> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(ShortLink::getShortCode, shortCodes)
                .set(ShortLink::getDeleted, 1)
                .set(ShortLink::getUpdatedTime, LocalDateTime.now());
        shortLinkMapper.update(null, wrapper);
        
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

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
