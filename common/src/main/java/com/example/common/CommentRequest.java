package com.example.common;

import java.io.Serializable;

public class CommentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long noteId;
    private Long userId;
    private String content;

    public CommentRequest() {}

    public CommentRequest(Long noteId, Long userId, String content) {
        this.noteId = noteId;
        this.userId = userId;
        this.content = content;
    }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
