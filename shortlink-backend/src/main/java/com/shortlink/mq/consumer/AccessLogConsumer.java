package com.shortlink.mq.consumer;

import com.rabbitmq.client.Channel;
import com.shortlink.common.config.RabbitMQConfig;
import com.shortlink.entity.AccessLog;
import com.shortlink.mapper.AccessLogMapper;
import com.shortlink.mq.message.AccessLogMessage;
import com.shortlink.util.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor // 为final方法提供构造器注入
public class AccessLogConsumer {

    // 用来把访问记录写入数据库
    private final AccessLogMapper accessLogMapper;
    // 用来更新 Redis 中的 PV（访问次数）和 UV（独立访客）数据
    private final StringRedisTemplate stringRedisTemplate;
    // 用来把失败的消息重新发回队列，实现重试
    private final RabbitTemplate rabbitTemplate;

    // Redis 中 PV 数据的键前缀，后面拼接短链接编码，例如 shortlink:pv:abc123
    private static final String PV_KEY_PREFIX = "shortlink:pv:";
    // Redis 中 UV 数据的键前缀，后面拼接短链接编码
    private static final String UV_KEY_PREFIX = "shortlink:uv:";
    // 消息头部中记录重试次数的字段名
    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    // 最大重试次数，超过这个次数就直接丢弃消息，防止死循环
    private static final int MAX_RETRY = 3;

    // 监听访问日志队列，收到消息后处理一次访问记录
    // - message - RabbitMQ 传来的访问日志消息，包含短链接编码、IP、User-Agent 等信息
    // - amqpMessage - AMQP 原始消息对象，用来读取重试次数等头部信息
    // - channel - RabbitMQ 信道，用来手动确认消息处理完毕（ack）
    // - 用 @RabbitListener 监听 ACCESS_LOG_QUEUE 队列，收到消息自动调用这个方法
    // - 这里使用手动 ack 模式，处理成功才确认，避免消息丢失
    @RabbitListener(queues = RabbitMQConfig.ACCESS_LOG_QUEUE) // 将方法标记为消息消费者
    public void consume(AccessLogMessage message, Message amqpMessage, Channel channel) throws IOException {
        // 从消息头部获取 deliveryTag，用来向 RabbitMQ 确认已处理完这条消息
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        try {
            log.debug("收到访问日志消息: {}", message.getShortCode());

            // 解析 User-Agent 字符串，从中提取设备类型、浏览器、操作系统等信息
            Map<String, String> uaInfo = UserAgentParser.parse(message.getUserAgent());

            // 组装要写入数据库的访问记录
            AccessLog accessLog = new AccessLog();
            accessLog.setShortCode(message.getShortCode());
            accessLog.setIp(message.getIp());
            accessLog.setUserAgent(message.getUserAgent());
            accessLog.setReferer(message.getReferer());
            // 从解析结果中取设备信息，没有就存 null
            accessLog.setDeviceType(uaInfo.get("deviceType"));
            accessLog.setBrowser(uaInfo.get("browser"));
            accessLog.setOs(uaInfo.get("os"));
            accessLog.setAccessTime(message.getAccessTime());

            // 1. 把访问记录写入数据库，留存详细日志
            accessLogMapper.insert(accessLog);

            // 2. 更新 Redis PV（页面访问次数）：直接用 INCR 命令自增
            String pvKey = PV_KEY_PREFIX + message.getShortCode();
            stringRedisTemplate.opsForValue().increment(pvKey);

            // 3. 更新 Redis UV（独立访客数）：用 HyperLogLog 数据结构，
            //    它占内存极小且能去重，适合统计海量 UV
            String uvKey = UV_KEY_PREFIX + message.getShortCode();
            stringRedisTemplate.opsForHyperLogLog().add(uvKey, message.getIp());

            // 4. 更新今日 UV，带上日期后缀方便按天统计
            String todayUvKey = UV_KEY_PREFIX + message.getShortCode() + ":" + LocalDate.now();
            stringRedisTemplate.opsForHyperLogLog().add(todayUvKey, message.getIp());
            // 给今日 UV 键设置 2 天过期，自动清理过期数据，节省内存
            stringRedisTemplate.expire(todayUvKey, 2, TimeUnit.DAYS);

            // 处理成功，手动确认消息，RabbitMQ 会从队列中移除这条消息
            channel.basicAck(deliveryTag, false);
            log.debug("访问日志处理成功: {}", message.getShortCode());

        } catch (Exception e) {
            // 处理失败时，根据已重试次数决定是重新投递还是丢弃
            int retryCount = getRetryCount(amqpMessage);
            if (retryCount < MAX_RETRY) {
                // 还没超过最大重试次数，把消息重新发回队列并带上新的重试次数
                log.warn("处理访问日志消息失败，第{}次重试: {}", retryCount + 1, message.getShortCode(), e);
                republishWithRetry(amqpMessage, retryCount + 1);
                // 重新投递后也要 ack 原始消息，避免重复消费
                channel.basicAck(deliveryTag, false);
            } else {
                // 已经重试够了，放弃这条消息，记录错误日志后确认丢弃
                log.error("处理访问日志消息失败，已达最大重试次数({})，丢弃消息: {}", MAX_RETRY, message.getShortCode(), e);
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    // 从消息头部读取当前已重试的次数
    // - message - AMQP 原始消息
    // - 返回头部记录的 x-retry-count 值，没有这个头部就返回 0
    // - 用消息头部保存重试次数而不是数据库，是因为这样不依赖外部存储，简单可靠
    private int getRetryCount(Message message) {
        Object count = message.getMessageProperties().getHeader(RETRY_COUNT_HEADER);
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    // 把处理失败的消息重新发回 RabbitMQ 队列，等待再次消费
    // - originalMessage - 原始消息，保留它的消息体
    // - retryCount - 更新后的重试次数，会写入消息头部
    // - 这里不是用 nack 让 RabbitMQ 自动重新投递，而是手动重新发送，
    //   这样可以精确控制重试次数，避免无限重试
    private void republishWithRetry(Message originalMessage, int retryCount) {
        // 复制原始消息的属性，并更新重试次数
        MessageProperties props = originalMessage.getMessageProperties();
        props.setHeader(RETRY_COUNT_HEADER, retryCount);
        // 用原消息体 + 更新后的属性，重新构造一条消息
        Message retryMessage = MessageBuilder.withBody(originalMessage.getBody())
                .andProperties(props)
                .build();
        // 重新发送到同一个交换机，走同样的路由键，相当于重新排队
        rabbitTemplate.send(RabbitMQConfig.ACCESS_LOG_EXCHANGE, RabbitMQConfig.ACCESS_LOG_ROUTING_KEY, retryMessage);
    }
}
