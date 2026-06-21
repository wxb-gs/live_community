package com.example.searchsync.consumer;

import com.example.searchsync.model.CanalMessage;
import com.example.searchsync.service.EsIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SearchSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncConsumer.class);

    private final EsIndexService esIndexService;

    public SearchSyncConsumer(EsIndexService esIndexService) {
        this.esIndexService = esIndexService;
    }

    @KafkaListener(topics = "search_sync", groupId = "search-sync-group")
    public void onMessage(String message) {
        try {
            CanalMessage msg = CanalMessage.fromJson(message);
            log.debug("Received: table={}, type={}, rows={}", msg.getTable(), msg.getType(),
                msg.getData() != null ? msg.getData().size() : 0);

            switch (msg.getTable()) {
                case "note":
                    if ("DELETE".equals(msg.getType())) {
                        esIndexService.deleteNote(msg.getData());
                    } else {
                        esIndexService.indexNote(msg.getData());
                    }
                    break;
                case "user_info":
                    if ("DELETE".equals(msg.getType())) {
                        esIndexService.deleteUser(msg.getData());
                    } else {
                        esIndexService.indexUser(msg.getData());
                    }
                    break;
                default:
                    log.warn("Unknown table: {}", msg.getTable());
            }
        } catch (Exception e) {
            log.error("Failed to process sync message", e);
        }
    }
}
