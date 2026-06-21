package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.WechatAuthService;
import org.springframework.stereotype.Component;

@Component
public class WechatLoginStrategy extends AbstractLoginStrategy {

    private final WechatAuthService wechatAuthService;

    public WechatLoginStrategy(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Override
    public LoginType getLoginType() { return LoginType.WECHAT; }

    @Override
    protected UserEntity doAuthenticate(LoginRequest req) {
        return wechatAuthService.authenticateAndRegister(req.getCode());
    }
}
