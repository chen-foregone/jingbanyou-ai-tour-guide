package cn.edu.gdou.jingbanyou.tourist.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * <p>
 * 定义游客端消息队列拓扑：交换机、队列、死信队列及绑定关系
 *
 * @author jingbanyou
 */
@Configuration
public class RabbitMQConfig {

    // ============ 常量 ============
    public static final String TOURIST_EXCHANGE = "jingbanyou.tourist.exchange";
    public static final String TOURIST_DLX = "jingbanyou.tourist.dlx";
    public static final String CHAT_QUEUE = "tourist.chat.queue";
    public static final String CHAT_DLQ = "tourist.chat.dlq";
    public static final String CHAT_ROUTING_KEY = "tourist.chat";

    // ============ 主交换机 ============
    @Bean
    public TopicExchange touristExchange() {
        return new TopicExchange(TOURIST_EXCHANGE);
    }

    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .deadLetterExchange(TOURIST_DLX)
                .deadLetterRoutingKey(CHAT_ROUTING_KEY + ".dlq")
                .build();
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder
                .bind(chatQueue())
                .to(touristExchange())
                .with(CHAT_ROUTING_KEY);
    }

    // ============ 死信交换机 + 队列（TTL 60s 后自动重试一次） ============
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(TOURIST_DLX);
    }

    @Bean
    public Queue chatDlq() {
        return QueueBuilder.durable(CHAT_DLQ)
                .ttl(60000)
                .deadLetterExchange(TOURIST_EXCHANGE)
                .deadLetterRoutingKey(CHAT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder
                .bind(chatDlq())
                .to(dlxExchange())
                .with(CHAT_ROUTING_KEY + ".dlq");
    }

    // ============ JSON 序列化 ============
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
