package com.shortlink.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
public class RabbitMQConfig {

    // 队列名称
    public static final String ACCESS_LOG_QUEUE = "shortlink.access.log.queue";
    
    // 交换机名称
    public static final String ACCESS_LOG_EXCHANGE = "shortlink.access.log.exchange";
    
    // 路由键
    public static final String ACCESS_LOG_ROUTING_KEY = "shortlink.access.log";

    /**
     * 访问日志队列
     */
    @Bean
    public Queue accessLogQueue() {
        return QueueBuilder.durable(ACCESS_LOG_QUEUE)
                .build();
    }

    /**
     * 访问日志交换机 - 直连交换机
     */
    @Bean
    public DirectExchange accessLogExchange() {
        return ExchangeBuilder.directExchange(ACCESS_LOG_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding accessLogBinding(Queue accessLogQueue, DirectExchange accessLogExchange) {
        return BindingBuilder.bind(accessLogQueue)
                .to(accessLogExchange)
                .with(ACCESS_LOG_ROUTING_KEY);
    }

    /**
     * 消息转换器 - JSON（支持 Java 8 时间类型）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块
        objectMapper.registerModule(new JavaTimeModule());
        // 禁用将日期写成时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
