package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.TaobaoAuthService;
import org.springframework.stereotype.Component;

@Component
public class TaobaoLoginStrategy extends AbstractLoginStrategy {

    private final TaobaoAuthService taobaoAuthService;

    public TaobaoLoginStrategy(TaobaoAuthService taobaoAuthService) {
        this.taobaoAuthService = taobaoAuthService;
    }

    @Override
    public LoginType getLoginType() { return LoginType.TAOBAO; }

    @Override
    protected UserEntity doAuthenticate(LoginRequest req) {
        return taobaoAuthService.authenticateAndRegister(req.getCode());
    }
}
