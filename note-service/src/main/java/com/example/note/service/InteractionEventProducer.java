package com.example.note.service;

import com.example.common.InteractionEvent;
import com.example.note.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 笔记互动事件的 Kafka 生产者。
 * 仅在 Redis toggle 成功后发送事件，消费者异步聚合写入 MySQL。
 */
@Service
public class InteractionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InteractionEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InteractionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(InteractionEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_INTERACTION, event.getTargetId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to send interaction event: targetId={}, userId={}, action={}",
                                event.getTargetId(), event.getUserId(), event.getInteractionType(), ex);
                    }
                });
        log.debug("Interaction event sent: {} {}:{} by user={} active={}",
                event.getInteractionType(), event.getTargetType(), event.getTargetId(),
                event.getUserId(), event.isActive());
    }
}
