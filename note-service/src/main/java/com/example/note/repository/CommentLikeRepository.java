package com.example.note.repository;

import com.example.note.entity.CommentLikeEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentLikeRepository extends CassandraRepository<CommentLikeEntity, Long> {

    /** 查某条评论的所有点赞用户 */
    @Query("SELECT * FROM comment_like WHERE comment_id = ?0")
    List<CommentLikeEntity> findByCommentId(Long commentId);

    /** 查用户对某条评论的点赞状态 */
    @Query("SELECT * FROM comment_like WHERE comment_id = ?0 AND user_id = ?1")
    CommentLikeEntity findByCommentIdAndUserId(Long commentId, Long userId);
}
