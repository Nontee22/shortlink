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
@RequiredArgsConstructor
public class AccessLogConsumer {

    private final AccessLogMapper accessLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    private static final String PV_KEY_PREFIX = "shortlink:pv:";
    private static final String UV_KEY_PREFIX = "shortlink:uv:";
    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    private static final int MAX_RETRY = 3;

    @RabbitListener(queues = RabbitMQConfig.ACCESS_LOG_QUEUE)
    public void consume(AccessLogMessage message, Message amqpMessage, Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        try {
            log.debug("收到访问日志消息: {}", message.getShortCode());

            Map<String, String> uaInfo = UserAgentParser.parse(message.getUserAgent());

            AccessLog accessLog = new AccessLog();
            accessLog.setShortCode(message.getShortCode());
            accessLog.setIp(message.getIp());
            accessLog.setUserAgent(message.getUserAgent());
            accessLog.setReferer(message.getReferer());
            accessLog.setDeviceType(uaInfo.get("deviceType"));
            accessLog.setBrowser(uaInfo.get("browser"));
            accessLog.setOs(uaInfo.get("os"));
            accessLog.setAccessTime(message.getAccessTime());

            accessLogMapper.insert(accessLog);

            String pvKey = PV_KEY_PREFIX + message.getShortCode();
            stringRedisTemplate.opsForValue().increment(pvKey);

            String uvKey = UV_KEY_PREFIX + message.getShortCode();
            stringRedisTemplate.opsForHyperLogLog().add(uvKey, message.getIp());

            String todayUvKey = UV_KEY_PREFIX + message.getShortCode() + ":" + LocalDate.now();
            stringRedisTemplate.opsForHyperLogLog().add(todayUvKey, message.getIp());
            stringRedisTemplate.expire(todayUvKey, 2, TimeUnit.DAYS);

            channel.basicAck(deliveryTag, false);
            log.debug("访问日志处理成功: {}", message.getShortCode());

        } catch (Exception e) {
            int retryCount = getRetryCount(amqpMessage);
            if (retryCount < MAX_RETRY) {
                log.warn("处理访问日志消息失败，第{}次重试: {}", retryCount + 1, message.getShortCode(), e);
                republishWithRetry(amqpMessage, retryCount + 1);
                channel.basicAck(deliveryTag, false);
            } else {
                log.error("处理访问日志消息失败，已达最大重试次数({})，丢弃消息: {}", MAX_RETRY, message.getShortCode(), e);
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    private int getRetryCount(Message message) {
        Object count = message.getMessageProperties().getHeader(RETRY_COUNT_HEADER);
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    private void republishWithRetry(Message originalMessage, int retryCount) {
        MessageProperties props = originalMessage.getMessageProperties();
        props.setHeader(RETRY_COUNT_HEADER, retryCount);
        Message retryMessage = MessageBuilder.withBody(originalMessage.getBody())
                .andProperties(props)
                .build();
        rabbitTemplate.send(RabbitMQConfig.ACCESS_LOG_EXCHANGE, RabbitMQConfig.ACCESS_LOG_ROUTING_KEY, retryMessage);
    }
}
