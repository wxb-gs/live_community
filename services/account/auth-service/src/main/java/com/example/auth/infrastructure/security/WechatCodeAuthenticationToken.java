package com.example.auth.infrastructure.security;

import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class WechatCodeAuthenticationToken extends AbstractAuthenticationToken {
  private final String code;

  public WechatCodeAuthenticationToken(String code) {
    super(Collections.emptyList());
    this.code = code;
  }

  @Override
  public Object getCredentials() {
    return code;
  }

  @Override
  public Object getPrincipal() {
    return null;
  }
}
