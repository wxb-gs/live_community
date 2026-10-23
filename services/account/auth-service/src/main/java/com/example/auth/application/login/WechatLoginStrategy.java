package com.example.auth.application.login;

import com.example.auth.application.WechatAuthService;
import com.example.auth.application.dto.LoginRequest;
import com.example.auth.domain.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class WechatLoginStrategy extends AbstractLoginStrategy {

  private final WechatAuthService wechatAuthService;

  public WechatLoginStrategy(WechatAuthService wechatAuthService) {
    this.wechatAuthService = wechatAuthService;
  }

  @Override
  public LoginType getLoginType() {
    return LoginType.WECHAT;
  }

  @Override
  protected UserEntity doAuthenticate(LoginRequest req) {
    return wechatAuthService.authenticateAndRegister(req.getCode());
  }
}
