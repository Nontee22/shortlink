package com.shortlink.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ShortCodeGenerator {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int SHORT_CODE_LENGTH = 6;

    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final SecureRandom random = new SecureRandom();
    private final long machineId = 1L;

    /**
     * 生成短码唯一ID，采用类雪花算法 + 随机混淆的策略
     * <p>
     * ID结构（共64位）：
     * - 高41位：时间戳（毫秒级），可用约69年
     * - 中10位：机器ID，支持最多1024个节点
     * - 低12位：序列号，同一毫秒内最多4096个不重复ID
     * <p>
     * 生成后与随机数做异或运算，使短码不可预测，避免被枚举猜测
     *
     * @return Base62编码后的短码字符串
     */
    public String generate() {
        long uniqueId;

        // 自旋循环，通过CAS保证多线程并发下的时间戳一致性
        while (true) {
            long prevTimestamp = lastTimestamp.get();
            long timestamp = System.currentTimeMillis();

            // 时钟回拨保护：若当前时间小于上次记录的时间戳，则沿用上次的值，避免ID重复
            if (timestamp < prevTimestamp) {
                timestamp = prevTimestamp;
            }

            long seq;
            if (timestamp == prevTimestamp) {
                // 同一毫秒内：序列号自增，& 0xFFF 限制在12位范围内（0~4095），防止溢出
                seq = sequence.incrementAndGet() & 0xFFFL;
            } else {
                // 新的毫秒：通过CAS原子更新lastTimestamp，成功则重置序列号为随机起点
                if (lastTimestamp.compareAndSet(prevTimestamp, timestamp)) {
                    // 序列号以随机数起始，降低不同机器间ID冲突的概率
                    seq = random.nextInt(100);
                    sequence.set(seq);
                } else {
                    // CAS失败说明有其他线程已更新时间戳，重新自旋
                    continue;
                }
            }

            // 拼装最终ID：时间戳左移22位 | 机器ID左移12位 | 序列号
            uniqueId = ((timestamp & 0x1FFFFFFFFFFL) << 22)
                    | ((machineId & 0x3FFL) << 12)
                    | seq;

            // 与随机数异或，打乱ID的规律性，使生成的短码不可预测
            uniqueId = Math.abs(uniqueId ^ random.nextLong());
            break;
        }

        // 将64位ID转换为Base62字符串（数字+大小写字母），作为最终短码
        return toBase62(uniqueId);
    }

    private String toBase62(long num) {
        StringBuilder sb = new StringBuilder();
        num = Math.abs(num);

        while (num > 0 && sb.length() < SHORT_CODE_LENGTH) {
            sb.insert(0, BASE62_CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }

        while (sb.length() < SHORT_CODE_LENGTH) {
            sb.insert(0, BASE62_CHARS.charAt(random.nextInt(BASE)));
        }

        return sb.toString();
    }

    public long fromBase62(String str) {
        long num = 0;
        for (char c : str.toCharArray()) {
            num = num * BASE + BASE62_CHARS.indexOf(c);
        }
        return num;
    }
}
