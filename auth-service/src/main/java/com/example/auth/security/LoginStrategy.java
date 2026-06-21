package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;

/**
 * Strategy pattern — each login method implements its own authentication logic.
 * <p>
 * Combined with Template Method: {@link AbstractLoginStrategy} provides the
 * common flow skeleton, while each concrete strategy fills in the
 * credential-validation and user-lookup steps.
 */
public interface LoginStrategy {

    LoginType getLoginType();

    /**
     * Authenticate and return the user. For social logins, auto-registers
     * a new user when the external identity is seen for the first time.
     */
    UserEntity authenticate(LoginRequest req);
}
