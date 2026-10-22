package com.example.note.comment.infrastructure;

import com.example.note.comment.domain.CommentSubjectRow;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CommentSubjectRepository {

  private static final Logger log = LoggerFactory.getLogger(CommentSubjectRepository.class);

  private final JdbcTemplate jdbc;

  private static final RowMapper<CommentSubjectRow> ROW_MAPPER =
      (rs, rowNum) -> {
        CommentSubjectRow r = new CommentSubjectRow();
        r.setObjId(rs.getLong("obj_id"));
        r.setObjType(rs.getInt("obj_type"));
        r.setCount(rs.getInt("count"));
        r.setRootCount(rs.getInt("root_count"));
        r.setState(rs.getInt("state"));
        return r;
      };

  public CommentSubjectRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── Read ──

  public Optional<CommentSubjectRow> findById(Long objId, Integer objType) {
    var rows =
        jdbc.query(
            "SELECT * FROM comment_subject WHERE obj_id = ? AND obj_type = ?",
            ROW_MAPPER,
            objId,
            objType);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  // ── Write ──

  private static final String UPSERT_SQL =
      "INSERT INTO comment_subject (obj_id, obj_type, count, root_count, state) "
          + "VALUES (?, ?, 0, 0, 0) "
          + "ON DUPLICATE KEY UPDATE obj_id = obj_id"; // no-op: ensure row exists

  /** 确保主题行存在（首次评论时调用） */
  public void ensureSubject(Long objId, Integer objType) {
    jdbc.update(UPSERT_SQL, objId, objType);
  }

  private static final String INCR_COUNT_SQL =
      "UPDATE comment_subject SET count = count + 1 WHERE obj_id = ? AND obj_type = ?";

  public void incrementCount(Long objId, Integer objType) {
    jdbc.update(INCR_COUNT_SQL, objId, objType);
  }

  private static final String INCR_ROOT_COUNT_SQL =
      "UPDATE comment_subject SET count = count + 1, root_count = root_count + 1 WHERE obj_id = ?"
          + " AND obj_type = ?";

  public void incrementRootCount(Long objId, Integer objType) {
    jdbc.update(INCR_ROOT_COUNT_SQL, objId, objType);
  }

  private static final String DECR_COUNT_SQL =
      "UPDATE comment_subject SET count = GREATEST(count - 1, 0) WHERE obj_id = ? AND obj_type = ?";

  public void decrementCount(Long objId, Integer objType) {
    jdbc.update(DECR_COUNT_SQL, objId, objType);
  }
}
