package com.example.common;

import java.io.Serializable;
import java.util.Map;

public class BatchStatusResponse implements Serializable {
    private Map<Long, TargetStatus> statuses;

    public BatchStatusResponse() {}

    public BatchStatusResponse(Map<Long, TargetStatus> statuses) {
        this.statuses = statuses;
    }

    public Map<Long, TargetStatus> getStatuses() { return statuses; }
    public void setStatuses(Map<Long, TargetStatus> statuses) { this.statuses = statuses; }

    public static class TargetStatus implements Serializable {
        private boolean active;
        private long count;

        public TargetStatus() {}
        public TargetStatus(boolean active, long count) {
            this.active = active;
            this.count = count;
        }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
