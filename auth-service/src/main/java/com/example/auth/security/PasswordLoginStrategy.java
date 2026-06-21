package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordLoginStrategy extends AbstractLoginStrategy {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordLoginStrategy(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginType getLoginType() { return LoginType.PASSWORD; }

    @Override
    protected UserEntity doAuthenticate(LoginRequest req) {
        UserEntity user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (user.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new DisabledException("账号已被禁用");
        }
        return user;
    }
}
