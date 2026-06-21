package com.example.note.service;

import com.example.common.InteractionEvent;
import com.example.common.InteractionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 通用互动服务 — 按 targetType 分流到不同持久化路径。
 *
 * 笔记 (targetType="note"):
 *   Redis + Lua 原子 toggle（热路径）
 *   → Kafka 事件 → 消费者窗口聚合 → MySQL 批量 UPSERT（冷路径）
 *
 * 评论 (targetType="comment"):
 *   @Async → 直接写 Cassandra counter + comment_like 表（无热点，无需 Redis）
 *
 * Redis Key 设计（仅笔记互动）:
 *   {prefix}:count:{targetType}:{targetId}      → 计数器 (String)
 *   {prefix}:users:{targetType}:{targetId}      → 互动用户集合 (Set)
 *   user:{prefix}:{userId}:{targetType}          → 用户互动历史 (Set)
 */
@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);

    private final StringRedisTemplate redis;
    private final RedisScript<List> toggleScript;
    private final RedisScript<List> batchStatusScript;
    private final InteractionEventProducer kafkaProducer;
    private final CommentLikeService commentLikeService;

    public InteractionService(StringRedisTemplate redis,
                              RedisScript<List> toggleScript,
                              RedisScript<List> batchStatusScript,
                              InteractionEventProducer kafkaProducer,
                              CommentLikeService commentLikeService) {
        this.redis = redis;
        this.toggleScript = toggleScript;
        this.batchStatusScript = batchStatusScript;
        this.kafkaProducer = kafkaProducer;
        this.commentLikeService = commentLikeService;
    }

    // ──────────── 公共 API ────────────

    /** 切换点赞/收藏状态，返回 (当前状态, 当前计数, 动作名) */
    public ToggleResult toggle(InteractionType type, String targetType, Long targetId, Long userId) {
        if ("comment".equals(targetType)) {
            return toggleComment(type, targetType, targetId, userId);
        }
        return toggleNote(type, targetType, targetId, userId);
    }

    /** 查询单个目标的互动状态 */
    public StatusResult getStatus(InteractionType type, String targetType, Long targetId, Long userId) {
        if ("comment".equals(targetType)) {
            CommentLikeService.LikeStatus s = commentLikeService.getStatus(targetId, userId);
            return new StatusResult(s.active(), s.count());
        }
        String prefix = prefix(type);
        boolean active = Boolean.TRUE.equals(
                redis.opsForSet().isMember(usersKey(prefix, targetType, targetId), userId.toString()));
        long count = getCountFromRedis(prefix, targetType, targetId);
        return new StatusResult(active, count);
    }

    /** 批量查询互动状态（Feed 流场景） */
    public Map<Long, StatusResult> batchStatus(InteractionType type, String targetType,
                                                List<Long> targetIds, Long userId) {
        if (targetIds.isEmpty()) return Collections.emptyMap();

        if ("comment".equals(targetType)) {
            Map<Long, StatusResult> map = new LinkedHashMap<>();
            Map<Long, Long> counts = commentLikeService.batchGetCounts(targetIds);
            for (Long tid : targetIds) {
                CommentLikeService.LikeStatus s = commentLikeService.getStatus(tid, userId);
                map.put(tid, new StatusResult(s.active(), s.count()));
            }
            return map;
        }

        String prefix = prefix(type);
        List<String> keys = new ArrayList<>();
        for (Long tid : targetIds) {
            keys.add(usersKey(prefix, targetType, tid));
            keys.add(countKey(prefix, targetType, tid));
        }
        List<Long> flat = redis.execute(batchStatusScript, keys, userId.toString());

        Map<Long, StatusResult> map = new LinkedHashMap<>();
        for (int i = 0; i < targetIds.size(); i++) {
            boolean active = flat.get(i * 2) == 1L;
            long count = flat.get(i * 2 + 1);
            map.put(targetIds.get(i), new StatusResult(active, count));
        }
        return map;
    }

    /** 获取用户互动过的目标ID列表（仅笔记，评论不需要此功能） */
    public List<Long> getUserInteractions(InteractionType type, String targetType, Long userId, int page, int size) {
        String prefix = prefix(type);
        String historyKey = userHistoryKey(prefix, userId, targetType);

        Set<String> members = redis.opsForSet().members(historyKey);
        if (members == null || members.isEmpty()) return Collections.emptyList();

        List<String> all = new ArrayList<>(members);
        int start = page * size;
        if (start >= all.size()) return Collections.emptyList();
        int end = Math.min(start + size, all.size());

        return all.subList(start, end).stream()
                .map(this::extractTargetId)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 获取计数器值 */
    public long getCount(InteractionType type, String targetType, Long targetId) {
        if ("comment".equals(targetType)) {
            Map<Long, Long> counts = commentLikeService.batchGetCounts(List.of(targetId));
            return counts.getOrDefault(targetId, 0L);
        }
        return getCountFromRedis(prefix(type), targetType, targetId);
    }

    // ──────────── 笔记互动（Redis + Kafka）────────────

    private ToggleResult toggleNote(InteractionType type, String targetType, Long targetId, Long userId) {
        String prefix = prefix(type);
        List<String> keys = List.of(
            countKey(prefix, targetType, targetId),
            usersKey(prefix, targetType, targetId),
            userHistoryKey(prefix, userId, targetType)
        );
        String activeAction = type.name().toLowerCase() + "d";
        String inactiveAction = "un" + activeAction;

        List<Object> raw = redis.execute(toggleScript, keys, userId.toString(), activeAction, inactiveAction);
        long count = ((Number) raw.get(0)).longValue();
        String action = raw.get(1).toString();
        boolean active = activeAction.equals(action);

        // 发送 Kafka 事件，异步聚合写入 MySQL
        kafkaProducer.publish(new InteractionEvent(
                type.name(), targetType, targetId, userId, active));

        log.debug("Toggle {} {}:{} by user={} → {} (count={})",
                type, targetType, targetId, userId, action, count);
        return new ToggleResult(active, count, action);
    }

    // ──────────── 评论互动（@Async → Cassandra）────────────

    private ToggleResult toggleComment(InteractionType type, String targetType, Long targetId, Long userId) {
        if (type != InteractionType.LIKE) {
            throw new IllegalArgumentException("Comment only supports LIKE, not " + type);
        }
        // 异步写 Cassandra，同步返回当前状态
        CommentLikeService.LikeStatus before = commentLikeService.getStatus(targetId, userId);
        commentLikeService.toggleAsync(targetId, userId);

        boolean active = !before.active();
        long count = before.count() + (active ? 1 : -1);
        if (count < 0) count = 0;
        String action = active ? "liked" : "unliked";

        log.debug("Toggle comment like {} by user={} → {} (count={})",
                targetId, userId, action, count);
        return new ToggleResult(active, count, action);
    }

    // ──────────── Redis Key 构建 ────────────

    static String prefix(InteractionType type) {
        return switch (type) {
            case LIKE -> "like";
            case FAVORITE -> "fav";
        };
    }

    static String countKey(String prefix, String targetType, Long targetId) {
        return prefix + ":count:" + targetType + ":" + targetId;
    }

    static String usersKey(String prefix, String targetType, Long targetId) {
        return prefix + ":users:" + targetType + ":" + targetId;
    }

    static String userHistoryKey(String prefix, Long userId, String targetType) {
        return "user:" + prefix + ":" + userId + ":" + targetType;
    }

    // ──────────── 辅助方法 ────────────

    private long getCountFromRedis(String prefix, String targetType, Long targetId) {
        String val = redis.opsForValue().get(countKey(prefix, targetType, targetId));
        return val != null ? Long.parseLong(val) : 0L;
    }

    private Long extractTargetId(String counterKey) {
        String[] parts = counterKey.split(":");
        if (parts.length >= 4) {
            try {
                return Long.parseLong(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ──────────── 结果类型 ────────────

    public record ToggleResult(boolean active, long count, String action) {}
    public record StatusResult(boolean active, long count) {}
}
