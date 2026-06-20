package com.example.auth.dto;

public class WechatUserInfo {
    private String openid;
    private String unionid;
    private String nickname;
    private String headimgurl;

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getHeadimgurl() { return headimgurl; }
    public void setHeadimgurl(String headimgurl) { this.headimgurl = headimgurl; }
}
