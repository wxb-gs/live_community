package com.example.note.comment.infrastructure;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 评论内容仓库 — 管理 comment_content 表。 内容表只按 comment_id 主键查询，不参与排序和分页。 */
@Repository
public class CommentContentRepository {

  private static final Logger log = LoggerFactory.getLogger(CommentContentRepository.class);

  private final JdbcTemplate jdbc;

  public CommentContentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 按 ID 查内容 */
  public Optional<String> findContent(Long commentId) {
    var rows =
        jdbc.query(
            "SELECT message FROM comment_content WHERE comment_id = ?",
            (rs, rowNum) -> rs.getString("message"),
            commentId);
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
  }

  /** 批量查询内容，返回 commentId -> message 映射 */
  public Map<Long, String> batchGetContent(List<Long> commentIds) {
    if (commentIds.isEmpty()) return Collections.emptyMap();
    String sql =
        "SELECT comment_id, message FROM comment_content WHERE comment_id IN ("
            + String.join(",", Collections.nCopies(commentIds.size(), "?"))
            + ")";
    List<Map<String, Object>> rows = jdbc.queryForList(sql, commentIds.toArray());
    Map<Long, String> result = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      Long cid = ((Number) row.get("comment_id")).longValue();
      String msg = (String) row.get("message");
      result.put(cid, msg);
    }
    return result;
  }

  /** 插入评论内容 */
  private static final String INSERT_SQL =
      "INSERT INTO comment_content (comment_id, message, extra) VALUES (?, ?, ?)";

  public void insert(Long commentId, String message, String extra) {
    jdbc.update(INSERT_SQL, commentId, message, extra);
  }

  /** 删除评论内容（物理删除） */
  private static final String DELETE_SQL = "DELETE FROM comment_content WHERE comment_id = ?";

  public void delete(Long commentId) {
    jdbc.update(DELETE_SQL, commentId);
  }
}
