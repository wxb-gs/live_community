package com.example.auth.infrastructure.security;

import com.example.auth.application.SmsService;
import com.example.auth.domain.UserEntity;
import com.example.auth.domain.UserPhoneEntity;
import com.example.auth.infrastructure.persistence.UserPhoneRepository;
import com.example.auth.infrastructure.persistence.UserRepository;
import com.example.common.IdResponse;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class PhoneSmsAuthenticationProvider implements AuthenticationProvider {

  private static final Logger log = LoggerFactory.getLogger(PhoneSmsAuthenticationProvider.class);

  private final SmsService smsService;
  private final UserRepository userRepository;
  private final UserPhoneRepository phoneRepository;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.leafclient.LeafIdClient leafRpcService;

  public PhoneSmsAuthenticationProvider(
      SmsService smsService, UserRepository userRepository, UserPhoneRepository phoneRepository) {
    this.smsService = smsService;
    this.userRepository = userRepository;
    this.phoneRepository = phoneRepository;
  }

  @Override
  public Authentication authenticate(Authentication auth) throws AuthenticationException {
    String phone = auth.getPrincipal().toString();
    String smsCode = auth.getCredentials().toString();

    if (!smsService.verifyCode(phone, smsCode)) {
      throw new BadCredentialsException("验证码错误或已过期");
    }

    var existing = phoneRepository.findByPhone(phone);
    if (existing.isPresent()) {
      return new UsernamePasswordAuthenticationToken(
          existing.get().getUserId(), null, Collections.emptyList());
    }

    // Auto-register
    long now = System.currentTimeMillis();
    IdResponse idResp = leafRpcService.generateSegmentId("user");
    long userId = idResp.getId();

    UserEntity user = new UserEntity();
    user.setUserId(userId);
    user.setUsername(null);
    user.setPassword(null);
    user.setNickname("用户" + phone.substring(phone.length() - 4));
    user.setStatus("ACTIVE");
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    userRepository.insert(user);

    IdResponse phoneIdResp = leafRpcService.generateSegmentId("phone");
    UserPhoneEntity phoneEntity = new UserPhoneEntity();
    phoneEntity.setPhoneId(phoneIdResp.getId());
    phoneEntity.setUserId(userId);
    phoneEntity.setPhone(phone);
    phoneEntity.setCreatedAt(now);
    phoneRepository.insert(phoneEntity);

    log.info("Phone auto-registered via provider: userId={}, phone={}", userId, phone);
    return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PhoneSmsAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
