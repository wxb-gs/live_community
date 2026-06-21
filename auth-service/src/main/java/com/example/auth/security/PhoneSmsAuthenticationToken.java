package com.example.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class PhoneSmsAuthenticationToken extends AbstractAuthenticationToken {
    private final String phone;
    private final String smsCode;

    public PhoneSmsAuthenticationToken(String phone, String smsCode) {
        super(Collections.emptyList());
        this.phone = phone;
        this.smsCode = smsCode;
        setAuthenticated(false);
    }

    @Override public Object getCredentials() { return smsCode; }
    @Override public Object getPrincipal() { return phone; }
}
