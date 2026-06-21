package com.example.note.service;

import com.example.note.config.KafkaTopicConfig;
import com.example.note.entity.NoteRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记变更的 Kafka 异步生产者。
 * 发送 CanalMessage 兼容格式到 search_sync topic，
 * search-sync-service 消费后索引到 ES。
 */
@Service
public class NoteSyncProducer {

    private static final Logger log = LoggerFactory.getLogger(NoteSyncProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NoteSyncProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    public void sendInsertOrUpdate(NoteRow row) {
        Map<String, Object> payload = buildPayload("INSERT", row);
        send(payload, row.getId());
    }

    @Async
    public void sendDelete(long noteId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", noteId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", "note");
        payload.put("type", "DELETE");
        payload.put("pkNames", List.of("id"));
        payload.put("data", List.of(data));
        send(payload, noteId);
    }

    private Map<String, Object> buildPayload(String type, NoteRow row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("user_id", row.getUserId());
        data.put("title", row.getTitle());
        data.put("content", row.getContent());
        data.put("summary", row.getSummary());
        data.put("tags", row.getTags());
        data.put("category", row.getCategory());
        data.put("view_count", row.getViewCount());
        data.put("like_count", row.getLikeCount());
        data.put("status", row.getStatus());
        data.put("created_at", row.getCreatedAt());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", "note");
        payload.put("type", type);
        payload.put("pkNames", List.of("id"));
        payload.put("data", List.of(data));
        return payload;
    }

    private void send(Map<String, Object> payload, long noteId) {
        kafkaTemplate.send(KafkaTopicConfig.TOPIC_SEARCH_SYNC, String.valueOf(noteId), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to send note sync event: noteId={}, type={}",
                                noteId, payload.get("type"), ex);
                    } else {
                        log.debug("Note sync event sent: noteId={}, type={}, partition={}, offset={}",
                                noteId, payload.get("type"),
                                result != null ? result.getRecordMetadata().partition() : -1,
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }
}
