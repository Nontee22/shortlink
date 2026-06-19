package com.shortlink.mq.producer;

import com.shortlink.common.config.RabbitMQConfig;
import com.shortlink.mq.message.AccessLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 访问日志消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送访问日志消息
     * @param message 消息体
     */
    public void send(AccessLogMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ACCESS_LOG_EXCHANGE,
                    RabbitMQConfig.ACCESS_LOG_ROUTING_KEY,
                    message
            );
            log.debug("发送访问日志消息成功: {}", message.getShortCode());
        } catch (Exception e) {
            log.error("发送访问日志消息失败: {}", message.getShortCode(), e);
        }
    }
}
