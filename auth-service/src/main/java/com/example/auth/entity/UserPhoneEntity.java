package com.example.auth.entity;

public class UserPhoneEntity {
    private Long phoneId;
    private Long userId;
    private String phone;
    private Long createdAt;

    public Long getPhoneId() { return phoneId; }
    public void setPhoneId(Long phoneId) { this.phoneId = phoneId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
