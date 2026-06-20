package com.example.auth.security;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.WechatAuthService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class WechatAuthenticationProvider implements AuthenticationProvider {

    private final WechatAuthService wechatAuthService;

    public WechatAuthenticationProvider(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String code = auth.getCredentials().toString();
        UserEntity user = wechatAuthService.authenticateAndRegister(code);
        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WechatCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
