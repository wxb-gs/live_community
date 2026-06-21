package com.example.note.service;

import com.example.common.InteractionEvent;
import com.example.note.config.KafkaTopicConfig;
import com.example.note.repository.InteractionRecordMysqlRepository;
import com.example.note.repository.NoteMysqlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka 消费者 — 窗口聚合笔记互动事件，批量写入 MySQL。
 *
 * 窗口策略：
 *   - 实时消费事件，按 (targetType, targetId, interactionType, userId) 去重
 *   - 每 30s 将窗口内累积的事件批量 UPSERT 到 MySQL
 *   - 同一窗口内同一用户的多次操作以最后一次为准
 */
@Service
public class InteractionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InteractionEventConsumer.class);

    private final InteractionRecordMysqlRepository mysqlRepo;
    private final NoteMysqlRepository noteMysqlRepo;

    /** 窗口缓冲区: key = "targetType:targetId:interactionType:userId" */
    private final ConcurrentHashMap<String, InteractionEvent> buffer = new ConcurrentHashMap<>();

    public InteractionEventConsumer(InteractionRecordMysqlRepository mysqlRepo,
                                     NoteMysqlRepository noteMysqlRepo) {
        this.mysqlRepo = mysqlRepo;
        this.noteMysqlRepo = noteMysqlRepo;
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_INTERACTION, groupId = "note-interaction-consumer")
    public void consume(InteractionEvent event) {
        // 只处理笔记互动（评论互动走 Cassandra 异步路径）
        if (!"note".equals(event.getTargetType())) return;

        String key = event.getTargetType() + ":" + event.getTargetId() + ":"
                + event.getInteractionType() + ":" + event.getUserId();
        buffer.put(key, event);
    }

    /** 每 30s 将窗口内累积的事件批量写入 MySQL */
    @Scheduled(fixedDelay = 30_000)
    public void flushToMySql() {
        if (buffer.isEmpty()) return;

        ConcurrentHashMap<String, InteractionEvent> snapshot = new ConcurrentHashMap<>(buffer);
        buffer.clear();

        List<Object[]> batchArgs = new ArrayList<>();
        for (InteractionEvent e : snapshot.values()) {
            long now = System.currentTimeMillis();
            String status = e.isActive() ? "ACTIVE" : "INACTIVE";
            batchArgs.add(new Object[]{
                    e.getTargetType(), e.getTargetId(), e.getInteractionType().toLowerCase(),
                    e.getUserId(), status, now, now
            });
        }

        try {
            mysqlRepo.batchUpsert(batchArgs);
            log.info("Flushed {} interaction events from Kafka window to MySQL", batchArgs.size());

            // Sync like_count to MySQL note table for LIKE/UNLIKE events
            for (InteractionEvent e : snapshot.values()) {
                if ("LIKE".equalsIgnoreCase(e.getInteractionType())) {
                    try {
                        noteMysqlRepo.addLikeCount(e.getTargetId(), e.isActive() ? 1 : -1);
                    } catch (Exception ex) {
                        log.error("Failed to sync like_count for noteId={}", e.getTargetId(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to flush {} events to MySQL, re-queuing", batchArgs.size(), ex);
            // 失败时放回缓冲区，下次重试
            snapshot.forEach(buffer::putIfAbsent);
        }
    }
}
