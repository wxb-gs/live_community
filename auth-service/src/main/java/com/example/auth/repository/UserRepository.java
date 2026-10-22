package com.example.auth.repository;

import com.example.auth.entity.UserEntity;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private final JdbcTemplate jdbc;
  private final com.example.outbox.Outbox outbox;

  public UserRepository(JdbcTemplate jdbc, com.example.outbox.Outbox outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  private final RowMapper<UserEntity> rowMapper =
      (rs, rowNum) -> {
        UserEntity u = new UserEntity();
        u.setUserId(rs.getLong("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setAvatar(rs.getString("avatar"));
        u.setStatus(rs.getString("status"));
        u.setCreatedAt(rs.getLong("created_at"));
        u.setUpdatedAt(rs.getLong("updated_at"));
        return u;
      };

  public Optional<UserEntity> findById(Long userId) {
    var list = jdbc.query("SELECT * FROM user_info WHERE user_id = ?", rowMapper, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  public Optional<UserEntity> findByUsername(String username) {
    var list = jdbc.query("SELECT * FROM user_info WHERE username = ?", rowMapper, username);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @org.springframework.transaction.annotation.Transactional
  public void insert(UserEntity user) {
    jdbc.update(
        "INSERT INTO user_info (user_id, username, password, nickname, avatar, status, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        user.getUserId(),
        user.getUsername(),
        user.getPassword(),
        user.getNickname(),
        user.getAvatar(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
    emit(user.getUserId());
  }

  @org.springframework.transaction.annotation.Transactional
  public void updateStatus(Long userId, String status) {
    jdbc.update(
        "UPDATE user_info SET status = ?,version=version+1 WHERE user_id = ?", status, userId);
    emit(userId);
  }

  public void emit(Long id) {
    var rows =
        jdbc.queryForList(
            "SELECT user_id,username,nickname,avatar,status,created_at,version FROM user_info WHERE"
                + " user_id=?",
            id);
    if (rows.isEmpty()) return;
    var d = new java.util.LinkedHashMap<String, Object>(rows.get(0));
    long version = ((Number) d.remove("version")).longValue();
    d.put("id", d.remove("user_id"));
    outbox.append(
        "search_documents",
        "user:" + id,
        new com.example.common.SearchDocumentEvent(
            1, "user", id, version, !"ACTIVE".equals(d.get("status")), d));
  }
}
