package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.UserPhoneEntity;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserPhoneRepository {

  private final JdbcTemplate jdbc;

  public UserPhoneRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private final RowMapper<UserPhoneEntity> rowMapper =
      (rs, rowNum) -> {
        UserPhoneEntity p = new UserPhoneEntity();
        p.setPhoneId(rs.getLong("phone_id"));
        p.setUserId(rs.getLong("user_id"));
        p.setPhone(rs.getString("phone"));
        p.setCreatedAt(rs.getLong("created_at"));
        return p;
      };

  public Optional<UserPhoneEntity> findByPhone(String phone) {
    var list = jdbc.query("SELECT * FROM user_phone WHERE phone = ?", rowMapper, phone);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  public void insert(UserPhoneEntity entity) {
    jdbc.update(
        "INSERT INTO user_phone (phone_id, user_id, phone, created_at) VALUES (?, ?, ?, ?)",
        entity.getPhoneId(),
        entity.getUserId(),
        entity.getPhone(),
        entity.getCreatedAt());
  }
}
