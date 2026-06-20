package com.example.note.entity;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("comment")
public class CommentEntity {

    @PrimaryKeyColumn(name = "note_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long noteId;

    @PrimaryKeyColumn(name = "comment_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private Long commentId;

    @Column("user_id")
    private Long userId;

    @Column("content")
    private String content;

    @Column("created_at")
    private Long createdAt;

    public CommentEntity() {}

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
