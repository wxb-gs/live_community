package com.example.note.repository;

import com.example.note.entity.NoteRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NoteMysqlRepository {

    private static final Logger log = LoggerFactory.getLogger(NoteMysqlRepository.class);

    private final JdbcTemplate jdbc;

    private static final RowMapper<NoteRow> ROW_MAPPER = (rs, rowNum) -> {
        NoteRow r = new NoteRow();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getLong("user_id"));
        r.setTitle(rs.getString("title"));
        r.setContent(rs.getString("content"));
        r.setSummary(rs.getString("summary"));
        r.setTags(rs.getString("tags"));
        r.setCategory(rs.getString("category"));
        r.setObjectKey(rs.getString("object_key"));
        r.setViewCount(rs.getInt("view_count"));
        r.setLikeCount(rs.getInt("like_count"));
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getLong("created_at"));
        r.setUpdatedAt(rs.getLong("updated_at"));
        return r;
    };

    public NoteMysqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Read ──

    public Optional<NoteRow> findById(long noteId) {
        List<NoteRow> rows = jdbc.query(
            "SELECT * FROM note WHERE id = ?", ROW_MAPPER, noteId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<NoteRow> findPublished(int limit) {
        return jdbc.query(
            "SELECT * FROM note WHERE status = 'PUBLISHED' ORDER BY created_at DESC LIMIT ?",
            ROW_MAPPER, limit);
    }

    public List<NoteRow> findByUserId(long userId, int offset, int limit) {
        return jdbc.query(
            "SELECT * FROM note WHERE user_id = ? AND status = 'PUBLISHED' ORDER BY created_at DESC LIMIT ? OFFSET ?",
            ROW_MAPPER, userId, limit, offset);
    }

    // ── Write ──

    private static final String UPSERT_SQL =
        "INSERT INTO note (id, user_id, title, content, summary, tags, category, object_key, view_count, like_count, status, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), "
        + "summary = VALUES(summary), tags = VALUES(tags), category = VALUES(category), "
        + "object_key = COALESCE(VALUES(object_key), object_key), "
        + "status = VALUES(status), updated_at = VALUES(updated_at)";

    public void upsert(long id, long userId, String title, String content, String summary,
                        String tags, String category, String objectKey,
                        String status, long createdAt, long updatedAt) {
        jdbc.update(UPSERT_SQL, id, userId, title, content, summary,
            tags != null ? tags : "", category != null ? category : "general",
            objectKey,
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
