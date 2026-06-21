package com.example.auth.dto;

public class PhoneLoginRequest {
    private String phone;
    private String smsCode;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }
}
