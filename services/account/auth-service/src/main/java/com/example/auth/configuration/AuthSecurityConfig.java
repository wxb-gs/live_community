package com.example.auth.configuration;

import com.example.auth.infrastructure.security.DaoAuthenticationProvider;
import com.example.auth.infrastructure.security.JwtAuthFilter;
import com.example.auth.infrastructure.security.PhoneSmsAuthenticationProvider;
import com.example.auth.infrastructure.security.TaobaoAuthenticationProvider;
import com.example.auth.infrastructure.security.WechatAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class AuthSecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;

  public AuthSecurityConfig(JwtAuthFilter jwtAuthFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/wechat/url",
                        "/api/auth/taobao/url",
                        "/api/auth/phone/send-code")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthFilter>
      disableContainerRegistration() {
    var registration =
        new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwtAuthFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  AuthenticationManager authManager(
      DaoAuthenticationProvider daoProvider,
      WechatAuthenticationProvider wechatProvider,
      TaobaoAuthenticationProvider taobaoProvider,
      PhoneSmsAuthenticationProvider phoneSmsProvider) {
    return new ProviderManager(daoProvider, wechatProvider, taobaoProvider, phoneSmsProvider);
  }
}
