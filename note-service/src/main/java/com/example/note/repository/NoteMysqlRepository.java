package com.example.note.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * MySQL 笔记仓库 — 笔记元数据双写落库。
 * 使用 UPSERT（ON DUPLICATE KEY UPDATE）保证幂等。
 */
@Repository
public class NoteMysqlRepository {

    private static final Logger log = LoggerFactory.getLogger(NoteMysqlRepository.class);

    private final JdbcTemplate jdbc;

    public NoteMysqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT_SQL =
        "INSERT INTO note (id, user_id, title, content, summary, tags, category, view_count, like_count, status, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), "
        + "summary = VALUES(summary), tags = VALUES(tags), category = VALUES(category), "
        + "status = VALUES(status), updated_at = VALUES(updated_at)";

    public void upsert(long id, long userId, String title, String content, String summary,
                        String tags, String category, String status, long createdAt, long updatedAt) {
        jdbc.update(UPSERT_SQL, id, userId, title, content, summary,
            tags != null ? tags : "", category != null ? category : "general",
            status, createdAt, updatedAt);
    }

    private static final String UPDATE_VIEW_COUNT_SQL =
        "UPDATE note SET view_count = view_count + ? WHERE id = ?";

    public void addViewCount(long noteId, int delta) {
        jdbc.update(UPDATE_VIEW_COUNT_SQL, delta, noteId);
    }

    private static final String UPDATE_LIKE_COUNT_SQL =
        "UPDATE note SET like_count = like_count + ? WHERE id = ?";

    public void addLikeCount(long noteId, int delta) {
        jdbc.update(UPDATE_LIKE_COUNT_SQL, delta, noteId);
    }

    private static final String DELETE_SQL = "DELETE FROM note WHERE id = ?";

    public void delete(long noteId) {
        jdbc.update(DELETE_SQL, noteId);
    }
}
