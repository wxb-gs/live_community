package com.example.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class WechatCodeAuthenticationToken extends AbstractAuthenticationToken {
    private final String code;

    public WechatCodeAuthenticationToken(String code) {
        super(Collections.emptyList());
        this.code = code;
    }

    @Override public Object getCredentials() { return code; }
    @Override public Object getPrincipal() { return null; }
}
