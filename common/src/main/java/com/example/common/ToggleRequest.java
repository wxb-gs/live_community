package com.example.common;

import java.io.Serializable;

public class ToggleRequest implements Serializable {
    private String interactionType;   // "LIKE" | "FAVORITE"
    private String targetType;        // "note" | "comment"
    private Long targetId;
    private Long userId;

    public ToggleRequest() {}

    public ToggleRequest(String interactionType, String targetType, Long targetId, Long userId) {
        this.interactionType = interactionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.userId = userId;
    }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
