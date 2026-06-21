package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template Method — skeleton for the login flow.
 * Subclasses implement {@link #doAuthenticate(LoginRequest)} and optionally
 * override {@link #findExistingUser(LoginRequest)} for social logins that
 * need to look up by external identity.
 */
public abstract class AbstractLoginStrategy implements LoginStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public UserEntity authenticate(LoginRequest req) {
        log.debug("Authenticating with {}", getLoginType());
        UserEntity user = doAuthenticate(req);
        if (user == null) {
            throw new RuntimeException("认证失败");
        }
        return user;
    }

    /**
     * Subclass implements credential validation / external-API calls.
     * Must return a non-null UserEntity or throw.
     */
    protected abstract UserEntity doAuthenticate(LoginRequest req);
}
