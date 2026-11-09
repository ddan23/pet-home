package com.hnist.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑与消息转换配置：集中声明三个业务接入点的交换机、队列与绑定关系。
 * 接入点一：评论 AI 审核走持久化工作队列（direct），替代内存线程池队列，应用重启不丢审核任务；
 * 接入点二：站内通知走 fanout 扇出，后续扩展邮件、短信等渠道只需追加队列绑定，业务代码零改动；
 * 接入点三：订单支付超时走 TTL + 死信交换机（DLX）延迟消息，替代每分钟全表轮询的常态扫描。
 * 消息体统一 JSON 序列化（禁用 JDK 原生序列化），类型映射只信任 com.hnist 包，防止反序列化任意类型。
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    /** 接入点一：评论审核交换机（direct）。 */
    public static final String COMMENT_MODERATION_EXCHANGE = "comment.moderation.exchange";

    /** 接入点一：评论审核队列，持久化保存待审评论 id。 */
    public static final String COMMENT_MODERATION_QUEUE = "comment.moderation.queue";

    /** 接入点一：评论审核路由键。 */
    public static final String COMMENT_MODERATION_ROUTING_KEY = "comment.moderation";

    /** 接入点二：通知扇出交换机（fanout）。 */
    public static final String NOTIFICATION_FANOUT_EXCHANGE = "notification.fanout.exchange";

    /** 接入点二：通知落库队列，绑定扇出交换机；扩展新渠道时在此追加队列并绑定同一交换机。 */
    public static final String NOTIFICATION_WRITE_QUEUE = "notification.write.queue";

    /** 接入点三：支付超时延迟交换机（direct）。 */
    public static final String ORDER_PAY_TIMEOUT_EXCHANGE = "order.pay.timeout.exchange";

    /** 接入点三：支付超时延迟队列，只存消息不做消费，TTL 到期后转入死信交换机。 */
    public static final String ORDER_PAY_TIMEOUT_QUEUE = "order.pay.timeout.queue";

    /** 接入点三：支付超时路由键。 */
    public static final String ORDER_PAY_TIMEOUT_ROUTING_KEY = "order.pay.timeout";

    /** 接入点三：死信交换机，接收延迟队列中超期转发的订单消息。 */
    public static final String ORDER_CLOSE_EXCHANGE = "order.close.exchange";

    /** 接入点三：关单队列，消费者在此执行超时取消与库存释放。 */
    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";

    /** 接入点三：死信转发路由键。 */
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    /**
     * 延迟队列 TTL：10 分钟，与 OrderServiceImpl.PAYMENT_TIMEOUT_MINUTES 同口径，修改必须两侧同步。
     * 采用队列级 TTL 而非消息级 TTL：RabbitMQ 只检查队头消息是否过期，
     * 队列内混排不同 TTL 时短 TTL 消息会被前面的长 TTL 消息压住（队头阻塞），固定单值队列无此问题。
     */
    public static final long ORDER_PAY_TIMEOUT_TTL_MILLIS = 10L * 60 * 1000;

    /** 评论审核 direct 交换机：生产者按固定路由键投递，消费者只绑审核队列。 */
    @Bean
    public DirectExchange commentModerationExchange() {
        return new DirectExchange(COMMENT_MODERATION_EXCHANGE, true, false);
    }

    /** 评论审核队列：durable 持久化，broker 重启后排队的待审消息不丢。 */
    @Bean
    public Queue commentModerationQueue() {
        return QueueBuilder.durable(COMMENT_MODERATION_QUEUE).build();
    }

    /** 将审核队列按固定路由键绑定到审核交换机。 */
    @Bean
    public Binding commentModerationBinding() {
        return BindingBuilder.bind(commentModerationQueue())
                .to(commentModerationExchange()).with(COMMENT_MODERATION_ROUTING_KEY);
    }

    /** 通知扇出交换机：忽略路由键，向所有绑定队列广播同一条通知。 */
    @Bean
    public FanoutExchange notificationFanoutExchange() {
        return new FanoutExchange(NOTIFICATION_FANOUT_EXCHANGE, true, false);
    }

    /** 通知落库队列：扇出的第一个消费通道，后续邮件、短信渠道在此追加队列。 */
    @Bean
    public Queue notificationWriteQueue() {
        return QueueBuilder.durable(NOTIFICATION_WRITE_QUEUE).build();
    }

    /** 通知落库队列绑定到扇出交换机（fanout 绑定不指定路由键）。 */
    @Bean
    public Binding notificationWriteBinding() {
        return BindingBuilder.bind(notificationWriteQueue()).to(notificationFanoutExchange());
    }

    /** 支付超时延迟 direct 交换机：下单事务提交后的消息从这里进入延迟队列。 */
    @Bean
    public DirectExchange orderPayTimeoutExchange() {
        return new DirectExchange(ORDER_PAY_TIMEOUT_EXCHANGE, true, false);
    }

    /**
     * 支付超时延迟队列：靠三个声明参数完成延迟语义——
     * x-message-ttl 设定到期时长，x-dead-letter-exchange 指定死信去向，x-dead-letter-routing-key 指定死信路由键。
     * 队列不声明消费者，消息到期即被 broker 自动转入死信交换机。
     */
    @Bean
    public Queue orderPayTimeoutQueue() {
        return QueueBuilder.durable(ORDER_PAY_TIMEOUT_QUEUE)
                .withArgument("x-message-ttl", ORDER_PAY_TIMEOUT_TTL_MILLIS)
                .withArgument("x-dead-letter-exchange", ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    /** 延迟队列绑定到延迟交换机。 */
    @Bean
    public Binding orderPayTimeoutBinding() {
        return BindingBuilder.bind(orderPayTimeoutQueue())
                .to(orderPayTimeoutExchange()).with(ORDER_PAY_TIMEOUT_ROUTING_KEY);
    }

    /** 死信交换机：TTL 到期的订单消息由 broker 自动投递到这里，完成延迟到关单的转发。 */
    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(ORDER_CLOSE_EXCHANGE, true, false);
    }

    /** 关单队列：消费超时消息，执行条件更新取消与库存释放。 */
    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(ORDER_CLOSE_QUEUE).build();
    }

    /** 关单队列按死信路由键绑定到死信交换机。 */
    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue())
                .to(orderCloseExchange()).with(ORDER_CLOSE_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器：替代默认的 JDK 原生序列化，消息体可读、跨语言且无反序列化攻击面；
     * 类型映射只信任 com.hnist 包，broker 中被植入的其他类型头会被拒绝反序列化。
     */
    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hnist");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * 发布方可靠性钩子：publisher-confirm 开启后，broker 落盘失败会回调 confirm；
     * mandatory 开启后，不可路由的消息会回调 returns。两者只记日志供排查，
     * 业务自愈由既有兜底承担（评论转人工复核、订单兜底轮询），不依赖日志触发。
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateReliabilityCustomizer() {
        return rabbitTemplate -> {
            rabbitTemplate.setConfirmCallback((CorrelationData correlationData,
                    boolean ack, String cause) -> {
                if (!ack) {
                    log.error("RabbitMQ 消息确认失败，cause={}", cause);
                }
            });
            rabbitTemplate.setReturnsCallback(returned ->
                    log.error("RabbitMQ 消息不可路由：exchange={}，routingKey={}，replyText={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyText()));
        };
    }
}
