package com.example.note.entity;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * 评论点赞记录 — 哪个用户点赞了哪条评论。
 * 按 comment_id 分区，user_id 聚簇，方便查某条评论的所有点赞用户。
 */
@Table("comment_like")
public class CommentLikeEntity {

    @PrimaryKeyColumn(name = "comment_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private Long commentId;

    @PrimaryKeyColumn(name = "user_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private Long userId;

    @Column("status")
    private String status;       // "ACTIVE" | "INACTIVE"

    @Column("created_at")
    private Long createdAt;

    public CommentLikeEntity() {}

    public CommentLikeEntity(Long commentId, Long userId, String status, Long createdAt) {
        this.commentId = commentId;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
