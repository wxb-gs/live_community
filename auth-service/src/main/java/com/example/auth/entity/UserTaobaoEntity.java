package com.example.auth.entity;

public class UserTaobaoEntity {
    private Long taobaoId;
    private Long userId;
    private String openId;
    private String nick;
    private String avatar;
    private Long createdAt;

    public Long getTaobaoId() { return taobaoId; }
    public void setTaobaoId(Long taobaoId) { this.taobaoId = taobaoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
