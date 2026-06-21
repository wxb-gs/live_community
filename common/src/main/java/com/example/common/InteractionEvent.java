package com.example.common;

import java.io.Serializable;

/**
 * Kafka 互动事件 — 笔记点赞/收藏的异步消息。
 * 消费者收到后批量聚合写入 MySQL。
 */
public class InteractionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String interactionType;   // LIKE / FAVORITE
    private String targetType;        // note
    private Long targetId;
    private Long userId;
    private boolean active;           // true=add, false=remove
    private long timestamp;

    public InteractionEvent() {}

    public InteractionEvent(String interactionType, String targetType, Long targetId,
                            Long userId, boolean active) {
        this.interactionType = interactionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.userId = userId;
        this.active = active;
        this.timestamp = System.currentTimeMillis();
    }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
