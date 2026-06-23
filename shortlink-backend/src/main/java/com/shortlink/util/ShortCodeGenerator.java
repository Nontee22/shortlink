package com.shortlink.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 短码生成器 — 基于 Snowflake 算法变体的全局唯一 ID 生成器。
 */
@Component
public class ShortCodeGenerator {

    /** Base62 字符集：数字(0-9) + 大写字母(A-Z) + 小写字母(a-z)，共 62 个字符 */
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    /** 进制基数 */
    private static final int BASE = 62;
    /** 短码固定长度 */
    private static final int SHORT_CODE_LENGTH = 6;

    /** 序列号（同一毫秒内的递增计数器，12 位） */
    private final AtomicLong sequence = new AtomicLong(0);
    /** 上一次生成的时间戳（毫秒），初始值 -1 表示未生成过 */
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    /** 加密安全的随机数生成器 */
    private final SecureRandom random = new SecureRandom();
    /** 机器标识（分布式部署时每台机器设不同值，10 位，最多 1024 台） */
    private final long machineId = 1L;

    /**
     * 生成全局唯一的短码
     */
    public String generate() {
        long uniqueId;

        while (true) {
            long prevTimestamp = lastTimestamp.get(); // 获取上一次时间戳
            long timestamp = System.currentTimeMillis(); // 获取当前时间戳

            // 时钟回拨保护：如果当前时间小于上次时间，沿用上次时间戳
            if (timestamp < prevTimestamp) {
                timestamp = prevTimestamp;
            }

            long seq;
            if (timestamp == prevTimestamp) {
                // 同一毫秒内：序列号自增（& 0xFFF 确保只取低 12 位）
                seq = sequence.incrementAndGet() & 0xFFFL;
            } else {
                // lastTimestamp 为需要读写的内存变量（v)，prevTimestamp 为线程在操作前读取到的旧值（A），timestamp 为线程希望写入的新值（B）
                if (lastTimestamp.compareAndSet(prevTimestamp, timestamp)) {
                    // 序列号从一个随机值起步，避免规律性，增强不可预测性
                    seq = random.nextInt(100);
                    sequence.set(seq);
                } else {
                    // CAS 失败说明其他线程已更新，重新循环
                    continue;
                }
            }

            // 位运算组装唯一 ID
            uniqueId = ((timestamp & 0x1FFFFFFFFFFL) << 22)    // 时间戳占高 41 位，左移 22 位
                    | ((machineId & 0x3FFL) << 12)             // 机器 ID 占中间 10 位，左移 12 位
                    | seq;                                     // 序列号占低 12 位

            // 与随机数 XOR 打散，让输出不可预测（防止被人通过短码反推生成规律）
            uniqueId = Math.abs(uniqueId ^ random.nextLong());
            break;
        }

        // 将 Long 型 ID 转换为 6 位 Base62 字符串
        return toBase62(uniqueId);
    }

    /**
     * 将 Long 型数值转换为 6 位 Base62 字符串
     */
    private String toBase62(long num) {
        StringBuilder sb = new StringBuilder();
        num = Math.abs(num);

        // 辗转相除法：num % 62 得到当前位字符，num / 62 进入下一轮
        while (num > 0 && sb.length() < SHORT_CODE_LENGTH) {
            sb.insert(0, BASE62_CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }

        // 不足 6 位时用随机字符填充前导位，进一步打散规律
        while (sb.length() < SHORT_CODE_LENGTH) {
            sb.insert(0, BASE62_CHARS.charAt(random.nextInt(BASE)));
        }

        return sb.toString();
    }

    /**
     * 将 Base62 短码反解析为原始 Long 值（用于调试或逆向查询）
     */
    public long fromBase62(String str) {
        long num = 0;
        for (char c : str.toCharArray()) {
            num = num * BASE + BASE62_CHARS.indexOf(c);
        }
        return num;
    }
}
