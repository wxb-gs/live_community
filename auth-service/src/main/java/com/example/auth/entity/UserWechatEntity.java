package com.example.auth.entity;

public class UserWechatEntity {
    private Long wechatId;
    private Long userId;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatar;
    private Long createdAt;

    public Long getWechatId() { return wechatId; }
    public void setWechatId(Long wechatId) { this.wechatId = wechatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
