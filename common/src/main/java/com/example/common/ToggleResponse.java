package com.example.common;

import java.io.Serializable;

public class ToggleResponse implements Serializable {
    private boolean active;      // 当前状态: true=已点赞/已收藏
    private long count;          // 当前总数
    private String action;       // "liked" | "unliked" | "favorited" | "unfavorited"

    public ToggleResponse() {}

    public ToggleResponse(boolean active, long count, String action) {
        this.active = active;
        this.count = count;
        this.action = action;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
