package com.example.common;

import java.io.Serializable;
import java.util.List;

public class BatchStatusRequest implements Serializable {
    private String interactionType;
    private String targetType;
    private List<Long> targetIds;
    private Long userId;

    public BatchStatusRequest() {}

    public BatchStatusRequest(String interactionType, String targetType, List<Long> targetIds, Long userId) {
        this.interactionType = interactionType;
        this.targetType = targetType;
        this.targetIds = targetIds;
        this.userId = userId;
    }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public List<Long> getTargetIds() { return targetIds; }
    public void setTargetIds(List<Long> targetIds) { this.targetIds = targetIds; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
