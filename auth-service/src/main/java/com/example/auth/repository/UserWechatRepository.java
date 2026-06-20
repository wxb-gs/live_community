package com.example.auth.repository;

import com.example.auth.entity.UserWechatEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserWechatRepository {

    private final JdbcTemplate jdbc;

    public UserWechatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<UserWechatEntity> rowMapper = (rs, rowNum) -> {
        UserWechatEntity w = new UserWechatEntity();
        w.setWechatId(rs.getLong("wechat_id"));
        w.setUserId(rs.getLong("user_id"));
        w.setOpenid(rs.getString("openid"));
        w.setUnionid(rs.getString("unionid"));
        w.setNickname(rs.getString("nickname"));
        w.setAvatar(rs.getString("avatar"));
        w.setCreatedAt(rs.getLong("created_at"));
        return w;
    };

    public Optional<UserWechatEntity> findByOpenid(String openid) {
        var list = jdbc.query("SELECT * FROM user_wechat WHERE openid = ?", rowMapper, openid);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insert(UserWechatEntity entity) {
        jdbc.update(
            "INSERT INTO user_wechat (wechat_id, user_id, openid, unionid, nickname, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            entity.getWechatId(), entity.getUserId(), entity.getOpenid(),
            entity.getUnionid(), entity.getNickname(), entity.getAvatar(), entity.getCreatedAt()
        );
    }
}
