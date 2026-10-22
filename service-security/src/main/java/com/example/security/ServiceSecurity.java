package com.example.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Import(ApiErrors.class)
@Configuration
public class ServiceSecurity {
  @Bean
  SecurityFilterChain serviceChain(HttpSecurity http, @Value("${jwt.secret}") String secret)
      throws Exception {
    var jwt =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws IOException, ServletException {
            try {
              long userId = JwtTokens.userId(secret, request.getHeader("Authorization"));
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (RuntimeException e) {
              response.setStatus(401);
              response.setContentType("application/json;charset=UTF-8");
              response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized\"}");
              return;
            }
            chain.doFilter(request, response);
          }
        };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
