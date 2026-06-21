package com.example.auth.repository;

import com.example.auth.entity.UserTaobaoEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserTaobaoRepository {

    private final JdbcTemplate jdbc;

    public UserTaobaoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UserTaobaoEntity> rowMapper = (rs, rowNum) -> {
        UserTaobaoEntity t = new UserTaobaoEntity();
        t.setTaobaoId(rs.getLong("taobao_id"));
        t.setUserId(rs.getLong("user_id"));
        t.setOpenId(rs.getString("open_id"));
        t.setNick(rs.getString("nick"));
        t.setAvatar(rs.getString("avatar"));
        t.setCreatedAt(rs.getLong("created_at"));
        return t;
    };

    public Optional<UserTaobaoEntity> findByOpenId(String openId) {
        var list = jdbc.query("SELECT * FROM user_taobao WHERE open_id = ?", rowMapper, openId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insert(UserTaobaoEntity entity) {
        jdbc.update(
            "INSERT INTO user_taobao (taobao_id, user_id, open_id, nick, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            entity.getTaobaoId(), entity.getUserId(), entity.getOpenId(),
            entity.getNick(), entity.getAvatar(), entity.getCreatedAt()
        );
    }
}
