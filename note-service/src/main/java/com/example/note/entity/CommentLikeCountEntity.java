package com.example.note.entity;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * 评论点赞计数器 — Cassandra counter 表。
 * 与 comment_like 表分离（Cassandra counter 列不能与普通列混用）。
 */
@Table("comment_like_count")
public class CommentLikeCountEntity {

    @PrimaryKey
    @Column("comment_id")
    private Long commentId;

    @Column("like_count")
    private Long likeCount;

    public CommentLikeCountEntity() {}

    public CommentLikeCountEntity(Long commentId, Long likeCount) {
        this.commentId = commentId;
        this.likeCount = likeCount;
    }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
}
