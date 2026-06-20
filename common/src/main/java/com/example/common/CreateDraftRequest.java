package com.example.common;

import java.io.Serializable;

public class CreateDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String title;
    private String content;

    public CreateDraftRequest() {}

    public CreateDraftRequest(Long userId, String title, String content) {
        this.userId = userId;
        this.title = title;
        this.content = content;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
