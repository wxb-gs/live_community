package com.example.note.comment.infrastructure;

import com.example.note.comment.domain.CommentIndexRow;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CommentIndexRepository {

  private static final Logger log = LoggerFactory.getLogger(CommentIndexRepository.class);

  private final JdbcTemplate jdbc;

  private static final RowMapper<CommentIndexRow> ROW_MAPPER =
      (rs, rowNum) -> {
        CommentIndexRow r = new CommentIndexRow();
        r.setId(rs.getLong("id"));
        r.setObjId(rs.getLong("obj_id"));
        r.setObjType(rs.getInt("obj_type"));
        r.setRootId(rs.getLong("root_id"));
        r.setParentId(rs.getLong("parent_id"));
        r.setUserId(rs.getLong("user_id"));
        r.setLikeCount(rs.getLong("like_count"));
        r.setFloor(rs.getInt("floor"));
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) r.setCreateTime(ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) r.setUpdateTime(ut.toLocalDateTime());
        r.setState(rs.getInt("state"));
        return r;
      };

  public CommentIndexRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── Read ──

  public Optional<CommentIndexRow> findById(Long id) {
    var rows = jdbc.query("SELECT * FROM comment_index WHERE id = ?", ROW_MAPPER, id);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** 分页拉取根评论（root_id=0），按创建时间倒序 */
  private static final String FIND_ROOT_COMMENTS_SQL =
      "SELECT * FROM comment_index "
          + "WHERE obj_id = ? AND obj_type = ? AND root_id = 0 AND state = 0 "
          + "ORDER BY create_time DESC LIMIT ? OFFSET ?";

  public List<CommentIndexRow> findRootComments(
      Long objId, Integer objType, int limit, int offset) {
    return jdbc.query(FIND_ROOT_COMMENTS_SQL, ROW_MAPPER, objId, objType, limit, offset);
  }

  /** 分页拉取某根评论下的子回复（不包含根评论自身） */
  private static final String FIND_REPLIES_SQL =
      "SELECT * FROM comment_index "
          + "WHERE root_id = ? AND id != ? AND state = 0 "
          + "ORDER BY create_time ASC LIMIT ? OFFSET ?";

  public List<CommentIndexRow> findReplies(Long rootId, int limit, int offset) {
    return jdbc.query(FIND_REPLIES_SQL, ROW_MAPPER, rootId, rootId, limit, offset);
  }

  /** 批量查询根评论的前N条子回复预览（盖楼折叠） */
  private static final String FIND_PREVIEW_REPLIES_SQL =
      "SELECT * FROM comment_index "
          + "WHERE root_id = ? AND id != ? AND state = 0 "
          + "ORDER BY create_time ASC LIMIT ?";

  public List<CommentIndexRow> findPreviewReplies(Long rootId, int limit) {
    return jdbc.query(FIND_PREVIEW_REPLIES_SQL, ROW_MAPPER, rootId, rootId, limit);
  }

  /** 统计某根评论下的回复总数 */
  private static final String COUNT_REPLIES_SQL =
      "SELECT COUNT(*) FROM comment_index WHERE root_id = ? AND state = 0";

  public int countReplies(Long rootId) {
    Integer count = jdbc.queryForObject(COUNT_REPLIES_SQL, Integer.class, rootId);
    return count != null ? count : 0;
  }

  // ── Write ──

  private static final String INSERT_SQL =
      "INSERT INTO comment_index (id, obj_id, obj_type, root_id, parent_id, user_id, "
          + "floor, create_time, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

  public void insert(CommentIndexRow row) {
    jdbc.update(
        INSERT_SQL,
        row.getId(),
        row.getObjId(),
        row.getObjType(),
        row.getRootId(),
        row.getParentId(),
        row.getUserId(),
        row.getFloor(),
        Timestamp.valueOf(row.getCreateTime()),
        row.getState());
  }

  /** 获取当前最大楼层号 */
  private static final String MAX_FLOOR_SQL =
      "SELECT COALESCE(MAX(floor), 0) FROM comment_index WHERE obj_id = ? AND obj_type = ? AND"
          + " root_id = 0";

  public int getMaxFloor(Long objId, Integer objType) {
    Integer max = jdbc.queryForObject(MAX_FLOOR_SQL, Integer.class, objId, objType);
    return max != null ? max : 0;
  }

  // ── 点赞数同步 ──

  private static final String UPDATE_LIKE_COUNT_SQL =
      "UPDATE comment_index SET like_count = like_count + ?, update_time = NOW(3) WHERE id = ?";

  public void addLikeCount(Long commentId, int delta) {
    jdbc.update(UPDATE_LIKE_COUNT_SQL, delta, commentId);
  }

  /** 批量更新点赞数（从 Redis 同步） */
  public void batchUpdateLikeCount(List<Object[]> batch) {
    jdbc.batchUpdate(UPDATE_LIKE_COUNT_SQL, batch);
  }

  // ── 状态变更 ──

  private static final String UPDATE_STATE_SQL =
      "UPDATE comment_index SET state = ?, update_time = NOW(3) WHERE id = ?";

  public void updateState(Long commentId, int state) {
    jdbc.update(UPDATE_STATE_SQL, state, commentId);
  }

  public void setLikeProjection(long id, long count, long version) {
    jdbc.update(
        "UPDATE comment_index SET like_count=?,like_version=? WHERE id=? AND like_version<?",
        count,
        version,
        id,
        version);
  }
}
