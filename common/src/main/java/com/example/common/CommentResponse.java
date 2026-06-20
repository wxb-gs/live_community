package com.example.common;

import java.io.Serializable;

public class CommentResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commentId;
    private Long noteId;
    private Long userId;
    private String content;
    private Long createdAt;

    public CommentResponse() {}

    public CommentResponse(Long commentId, Long noteId, Long userId, String content, Long createdAt) {
        this.commentId = commentId;
        this.noteId = noteId;
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
