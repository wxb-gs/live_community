package com.example.note.content.infrastructure;

import com.example.note.content.application.NoteSyncProducer;
import com.example.note.content.domain.NoteRow;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NoteMysqlRepository {
  private final JdbcTemplate jdbc;
  private final NoteSyncProducer sync;

  public NoteMysqlRepository(JdbcTemplate jdbc, NoteSyncProducer sync) {
    this.jdbc = jdbc;
    this.sync = sync;
  }

  private static final RowMapper<NoteRow> ROW_MAPPER =
      (rs, rowNum) -> {
        NoteRow r = new NoteRow();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getLong("user_id"));
        r.setTitle(rs.getString("title"));
        r.setContent(rs.getString("content"));
        r.setSummary(rs.getString("summary"));
        r.setTags(rs.getString("tags"));
        r.setCategory(rs.getString("category"));
        r.setObjectKey(rs.getString("object_key"));
        r.setViewCount(rs.getLong("view_count"));
        r.setLikeCount(rs.getLong("like_count"));
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getLong("created_at"));
        r.setUpdatedAt(rs.getLong("updated_at"));
        r.setFileId(rs.getString("file_id"));
        r.setVersion(rs.getLong("version"));
        return r;
      };

  public Optional<NoteRow> findById(long id) {
    return jdbc.query("SELECT * FROM note WHERE id=?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<NoteRow> findPublished(int offset, int limit) {
    return jdbc.query(
        "SELECT * FROM note WHERE status='PUBLISHED' ORDER BY created_at DESC,id DESC LIMIT ?"
            + " OFFSET ?",
        ROW_MAPPER,
        limit,
        offset);
  }

  public List<NoteRow> findByUserId(long userId, int offset, int limit) {
    return jdbc.query(
        "SELECT * FROM note WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        ROW_MAPPER,
        userId,
        limit,
        offset);
  }

  @Transactional
  public void upsert(
      long id,
      long userId,
      String title,
      String content,
      String summary,
      String tags,
      String category,
      String objectKey,
      String status,
      long createdAt,
      long updatedAt) {
    jdbc.update(
        "INSERT INTO"
            + " note(id,user_id,title,content,summary,tags,category,object_key,view_count,like_count,status,created_at,updated_at,version)"
            + " VALUES(?,?,?,?,?,?,?,?,0,0,?,?,?,1)",
        id,
        userId,
        title,
        content,
        summary,
        tags == null ? "" : tags,
        category == null ? "general" : category,
        objectKey,
        status,
        createdAt,
        updatedAt);
    emit(id);
  }

  @Transactional
  public NoteRow publish(long id, long userId, String fileId, String objectKey) {
    var row =
        jdbc.query("SELECT * FROM note WHERE id=? FOR UPDATE", ROW_MAPPER, id).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Note not found"));
    if (row.getUserId() != userId)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Author required");
    if ("PUBLISHED".equals(row.getStatus()) && Objects.equals(fileId, row.getFileId())) return row;
    if (!"DRAFT".equals(row.getStatus()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Note status conflict");
    int changed =
        jdbc.update(
            "UPDATE note SET"
                + " status='PUBLISHED',file_id=?,object_key=?,version=version+1,updated_at=? WHERE"
                + " id=? AND user_id=? AND status='DRAFT'",
            fileId,
            objectKey,
            System.currentTimeMillis(),
            id,
            userId);
    if (changed != 1)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Concurrent publish");
    emit(id);
    return findById(id).orElseThrow();
  }

  @Transactional
  public void setLikeProjection(long id, long count, long version) {
    if (jdbc.update(
            "UPDATE note SET like_count=?,like_version=?,version=version+1 WHERE id=? AND"
                + " like_version<?",
            count,
            version,
            id,
            version)
        > 0) emit(id);
  }

  @Transactional
  public void recordView(long id, long userId, String eventId) {
    if (eventId == null || !eventId.matches("[A-Za-z0-9-]{1,64}"))
      throw new IllegalArgumentException("Valid view eventId required");
    jdbc.update(
        "INSERT INTO note_view(event_id,note_id,user_id,created_at) VALUES(?,?,?,?) ON DUPLICATE"
            + " KEY UPDATE event_id=VALUES(event_id)",
        eventId,
        id,
        userId,
        System.currentTimeMillis());
    var row =
        jdbc.queryForMap(
            "SELECT note_id,user_id,applied FROM note_view WHERE event_id=? FOR UPDATE", eventId);
    if (((Number) row.get("note_id")).longValue() != id
        || ((Number) row.get("user_id")).longValue() != userId)
      throw new IllegalArgumentException("View id reused");
    if (((Number) row.get("applied")).intValue() == 0) {
      int n =
          jdbc.update(
              "UPDATE note SET view_count=view_count+1,version=version+1 WHERE id=? AND"
                  + " status='PUBLISHED'",
              id);
      if (n == 1) emit(id);
      jdbc.update("UPDATE note_view SET applied=1 WHERE event_id=?", eventId);
    }
  }

  @Transactional
  public void delete(long id) {
    jdbc.update("UPDATE note SET status='DELETED',version=version+1 WHERE id=?", id);
    emit(id);
  }

  private void emit(long id) {
    sync.sendInsertOrUpdate(findById(id).orElseThrow());
  }
}
