package com.example.auth.repository;

import com.example.auth.entity.UserEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UserEntity> rowMapper = (rs, rowNum) -> {
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

    public void insert(UserEntity user) {
        jdbc.update(
            "INSERT INTO user_info (user_id, username, password, nickname, avatar, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            user.getUserId(), user.getUsername(), user.getPassword(), user.getNickname(),
            user.getAvatar(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    public void updateStatus(Long userId, String status) {
        jdbc.update("UPDATE user_info SET status = ? WHERE user_id = ?", status, userId);
    }
}
