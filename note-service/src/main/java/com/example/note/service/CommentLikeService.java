package com.example.note.service;

import com.example.note.entity.CommentLikeEntity;
import com.example.note.repository.CommentLikeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论点赞服务 — 异步直接写 Cassandra（不经过 Redis）。
 *
 * 评论点赞无热点行问题（点赞分散到数百条评论），无需 Redis 挡热点。
 * 使用 Cassandra counter 表做原子计数，comment_like 表做用户状态记录。
 * Counter 操作通过 CqlTemplate 发原始 CQL（Cassandra counter 不支持 INSERT）。
 */
@Service
public class CommentLikeService {

    private static final Logger log = LoggerFactory.getLogger(CommentLikeService.class);

    private static final String INCR_COUNT =
            "UPDATE comment_like_count SET like_count = like_count + 1 WHERE comment_id = ?";
    private static final String DECR_COUNT =
            "UPDATE comment_like_count SET like_count = like_count - 1 WHERE comment_id = ?";
    private static final String GET_COUNT =
            "SELECT like_count FROM comment_like_count WHERE comment_id = ?";

    private final CommentLikeRepository likeRepo;
    private final CqlTemplate cqlTemplate;

    public CommentLikeService(CommentLikeRepository likeRepo, CqlTemplate cqlTemplate) {
        this.likeRepo = likeRepo;
        this.cqlTemplate = cqlTemplate;
    }

    /** 异步切换评论点赞状态 */
    @Async
    public void toggleAsync(Long commentId, Long userId) {
        CommentLikeEntity existing = likeRepo.findByCommentIdAndUserId(commentId, userId);

        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            existing.setStatus("INACTIVE");
            likeRepo.save(existing);
            cqlTemplate.execute(DECR_COUNT, commentId);
        } else {
            long now = System.currentTimeMillis();
            likeRepo.save(new CommentLikeEntity(commentId, userId, "ACTIVE", now));
            cqlTemplate.execute(INCR_COUNT, commentId);
        }
    }

    /** 同步查询单条评论的点赞状态 */
    public LikeStatus getStatus(Long commentId, Long userId) {
        CommentLikeEntity record = likeRepo.findByCommentIdAndUserId(commentId, userId);
        boolean active = record != null && "ACTIVE".equals(record.getStatus());
        long count = getCount(commentId);
        return new LikeStatus(active, count);
    }

    /** 批量查询评论点赞计数（用于笔记详情页展示评论列表） */
    public Map<Long, Long> batchGetCounts(List<Long> commentIds) {
        Map<Long, Long> result = new HashMap<>();
        for (Long cid : commentIds) {
            result.put(cid, getCount(cid));
        }
        return result;
    }

    private long getCount(Long commentId) {
        try {
            Long count = cqlTemplate.queryForObject(GET_COUNT, Long.class, commentId);
            return count != null ? count : 0L;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return 0L;
        }
    }

    public record LikeStatus(boolean active, long count) {}
}
