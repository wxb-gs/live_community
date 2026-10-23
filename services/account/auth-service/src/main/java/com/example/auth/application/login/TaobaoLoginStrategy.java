package com.example.auth.application.login;

import com.example.auth.application.TaobaoAuthService;
import com.example.auth.application.dto.LoginRequest;
import com.example.auth.domain.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class TaobaoLoginStrategy extends AbstractLoginStrategy {

  private final TaobaoAuthService taobaoAuthService;

  public TaobaoLoginStrategy(TaobaoAuthService taobaoAuthService) {
    this.taobaoAuthService = taobaoAuthService;
  }

  @Override
  public LoginType getLoginType() {
    return LoginType.TAOBAO;
  }

  @Override
  protected UserEntity doAuthenticate(LoginRequest req) {
    return taobaoAuthService.authenticateAndRegister(req.getCode());
  }
}
