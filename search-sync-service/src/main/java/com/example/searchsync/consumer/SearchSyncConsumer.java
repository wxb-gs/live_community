package com.example.searchsync.consumer;

import com.example.searchsync.model.CanalMessage;
import com.example.searchsync.service.EsIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SearchSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncConsumer.class);

    private final EsIndexService esIndexService;

    public SearchSyncConsumer(EsIndexService esIndexService) {
        this.esIndexService = esIndexService;
    }

    @KafkaListener(topics = "search_sync", groupId = "search-sync-group")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            CanalMessage msg = CanalMessage.fromJson(message);
            if (msg.getData() == null || msg.getData().isEmpty()) {
                ack.acknowledge();
                return;
            }

            log.debug("Received: table={}, type={}, rows={}", msg.getTable(), msg.getType(), msg.getData().size());

            boolean ok = switch (msg.getTable()) {
                case "note" -> "DELETE".equals(msg.getType())
                    ? esIndexService.deleteNote(msg.getData())
                    : esIndexService.indexNote(msg.getData());
                case "user_info" -> "DELETE".equals(msg.getType())
                    ? esIndexService.deleteUser(msg.getData())
                    : esIndexService.indexUser(msg.getData());
                default -> {
                    log.warn("Unknown table: {}", msg.getTable());
                    yield true;
                }
            };

            if (ok) {
                ack.acknowledge();
            } else {
                // 不 ack，消息会被重新投递；ES upsert 天然幂等
                log.warn("Sync failed, will retry: table={}, type={}", msg.getTable(), msg.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process sync message, will retry", e);
            // 不 ack，触发重试
        }
    }
}
