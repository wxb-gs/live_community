package com.example.auth.config;

import com.example.auth.security.DaoAuthenticationProvider;
import com.example.auth.security.JwtAuthFilter;
import com.example.auth.security.WechatAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public AuthSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register",
                                 "/api/auth/login",
                                 "/api/auth/wechat/url",
                                 "/api/auth/wechat/login",
                                 "/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout",
                                 "/api/auth/me").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager authManager(
            DaoAuthenticationProvider daoProvider,
            WechatAuthenticationProvider wechatProvider) {
        return new ProviderManager(daoProvider, wechatProvider);
    }
}
