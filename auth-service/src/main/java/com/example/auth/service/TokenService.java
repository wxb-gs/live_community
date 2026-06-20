package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {

    private final SecretKey secretKey;
    private final RedisTemplate<String, String> redis;

    @Value("${jwt.access-token-ttl:900}")
    private long accessTokenTtl;

    @Value("${jwt.refresh-token-ttl:604800}")
    private long refreshTokenTtl;

    public TokenService(SecretKey secretKey, RedisTemplate<String, String> redis) {
        this.secretKey = secretKey;
        this.redis = redis;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

    public TokenPair issueTokens(UserEntity user) {
        return new TokenPair(
                issueAccessToken(user),
                issueRefreshToken(user.getUserId()),
                accessTokenTtl
        );
    }

    String issueAccessToken(UserEntity user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(Map.of(
                        "username", user.getUsername() != null ? user.getUsername() : "",
                        "nickname", user.getNickname() != null ? user.getNickname() : "",
                        "avatar", user.getAvatar() != null ? user.getAvatar() : ""
                ))
                .subject(user.getUserId().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtl * 1000))
                .signWith(secretKey)
                .compact();
    }

    String issueRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String key = "refresh:" + tokenId;
        redis.opsForValue().set(key, userId.toString(), Duration.ofSeconds(refreshTokenTtl));
        redis.opsForSet().add("user_sessions:" + userId, tokenId);
        redis.expire("user_sessions:" + userId, Duration.ofSeconds(refreshTokenTtl));
        return tokenId;
    }

    public Long validateAndGetUserId(String refreshToken) {
        String key = "refresh:" + refreshToken;
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) return null;
        return Long.parseLong(userIdStr);
    }

    public void revokeRefreshToken(Long userId, String refreshToken) {
        redis.delete("refresh:" + refreshToken);
        redis.opsForSet().remove("user_sessions:" + userId, refreshToken);
    }

    public void revokeAllUserTokens(Long userId) {
        var tokenIds = redis.opsForSet().members("user_sessions:" + userId);
        if (tokenIds != null) {
            for (String tid : tokenIds) {
                redis.delete("refresh:" + tid);
            }
        }
        redis.delete("user_sessions:" + userId);
    }
}
