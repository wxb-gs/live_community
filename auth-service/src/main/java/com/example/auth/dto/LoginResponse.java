package com.example.auth.dto;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;

    public static class UserInfo {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
