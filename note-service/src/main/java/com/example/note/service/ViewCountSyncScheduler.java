package com.example.note.service;

import com.example.note.repository.NoteMysqlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@EnableScheduling
public class ViewCountSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ViewCountSyncScheduler.class);
    private static final String VIEW_KEY_PREFIX = "note:view:";

    private final StringRedisTemplate redisTemplate;
    private final NoteMysqlRepository noteMysqlRepository;

    public ViewCountSyncScheduler(StringRedisTemplate redisTemplate, NoteMysqlRepository noteMysqlRepository) {
        this.redisTemplate = redisTemplate;
        this.noteMysqlRepository = noteMysqlRepository;
    }

    @Scheduled(fixedRate = 300_000)
    public void syncViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        log.info("Syncing view counts for {} notes", keys.size());
        for (String key : keys) {
            try {
                long noteId = Long.parseLong(key.substring(VIEW_KEY_PREFIX.length()));
                String countStr = redisTemplate.opsForValue().getAndDelete(key);
                if (countStr != null) {
                    int delta = Integer.parseInt(countStr);
                    noteMysqlRepository.addViewCount(noteId, delta);
                }
            } catch (Exception e) {
                log.error("Failed to sync view count for key: {}", key, e);
            }
        }
    }
}
