package com.example.gateway;

import com.example.security.JwtTokens;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

@Configuration
public class GatewaySecurity {
  private static final Set<String> PUBLIC =
      Set.of(
          "/api/auth/login",
          "/api/auth/register",
          "/api/auth/refresh",
          "/api/auth/phone/send-code",
          "/api/auth/wechat/url",
          "/api/auth/taobao/url");

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http, @Value("${jwt.secret}") String secret) {
    WebFilter jwt =
        (exchange, chain) -> {
          if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS
              || !exchange.getRequest().getPath().value().startsWith("/api/")
              || PUBLIC.contains(exchange.getRequest().getPath().value()))
            return chain.filter(exchange);
          try {
            long id =
                JwtTokens.userId(
                    secret, exchange.getRequest().getHeaders().getFirst("Authorization"));
            var auth = new UsernamePasswordAuthenticationToken(id, null, List.of());
            return chain
                .filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
          } catch (RuntimeException e) {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
          }
        };
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(a -> a.anyExchange().permitAll())
        .addFilterAt(jwt, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }
}
