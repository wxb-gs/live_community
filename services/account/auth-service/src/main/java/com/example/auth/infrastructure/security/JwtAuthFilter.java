package com.example.auth.infrastructure.security;

import com.example.security.JwtTokens;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final String secret;

  public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
    this.secret = secret;
  }

  private static final Set<String> PUBLIC =
      Set.of(
          "/api/auth/register",
          "/api/auth/login",
          "/api/auth/refresh",
          "/api/auth/wechat/url",
          "/api/auth/taobao/url",
          "/api/auth/phone/send-code");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if (PUBLIC.contains(req.getRequestURI())) {
      chain.doFilter(req, res);
      return;
    }
    try {
      long id = JwtTokens.userId(secret, req.getHeader("Authorization"));
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(id, ""));
    } catch (RuntimeException e) {
      res.setStatus(401);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}
