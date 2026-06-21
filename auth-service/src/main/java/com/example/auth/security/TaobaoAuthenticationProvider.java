package com.example.auth.security;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.TaobaoAuthService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class TaobaoAuthenticationProvider implements AuthenticationProvider {

    private final TaobaoAuthService taobaoAuthService;

    public TaobaoAuthenticationProvider(TaobaoAuthService taobaoAuthService) {
        this.taobaoAuthService = taobaoAuthService;
    }

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String code = auth.getCredentials().toString();
        UserEntity user = taobaoAuthService.authenticateAndRegister(code);
        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TaobaoCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
