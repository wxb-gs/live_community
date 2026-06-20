package com.example.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final Long userId;
    private final String username;

    public JwtAuthenticationToken(Long userId, String username) {
        super(Collections.emptyList());
        this.userId = userId;
        this.username = username;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return userId; }
    public String getUsername() { return username; }
}
