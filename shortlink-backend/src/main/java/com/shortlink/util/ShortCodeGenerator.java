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

    public String generate() {
        long uniqueId;

        while (true) {
            long prevTimestamp = lastTimestamp.get();
            long timestamp = System.currentTimeMillis();

            if (timestamp < prevTimestamp) {
                timestamp = prevTimestamp;
            }

            long seq;
            if (timestamp == prevTimestamp) {
                seq = sequence.incrementAndGet() & 0xFFFL;
            } else {
                if (lastTimestamp.compareAndSet(prevTimestamp, timestamp)) {
                    seq = random.nextInt(100);
                    sequence.set(seq);
                } else {
                    continue;
                }
            }

            uniqueId = ((timestamp & 0x1FFFFFFFFFFL) << 22)
                    | ((machineId & 0x3FFL) << 12)
                    | seq;

            uniqueId = Math.abs(uniqueId ^ random.nextLong());
            break;
        }

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
