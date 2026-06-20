package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.WechatCodeAuthenticationToken;
import com.example.auth.service.TokenService;
import com.example.auth.service.WechatAuthService;
import com.example.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WechatAuthService wechatAuthService;

    public AuthController(AuthenticationManager authManager, TokenService tokenService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder,
                          WechatAuthService wechatAuthService) {
        this.authManager = authManager;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.wechatAuthService = wechatAuthService;
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Register will be integrated via leaf-service. Use POST /login for now.");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        Long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @PostMapping("/wechat/login")
    public Result<LoginResponse> wechatLogin(@RequestBody WechatLoginRequest req) {
        Authentication auth = authManager.authenticate(
                new WechatCodeAuthenticationToken(req.getCode()));
        Long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @GetMapping("/wechat/url")
    public Result<String> wechatUrl(@RequestParam String redirectUri) {
        String url = wechatAuthService.buildAuthUrl(redirectUri);
        return Result.ok(url);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshRequest req) {
        Long userId = tokenService.validateAndGetUserId(req.getRefreshToken());
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RefreshToken 无效或已过期");
        }

        tokenService.revokeRefreshToken(userId, req.getRefreshToken());

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestBody RefreshRequest req,
                                  @AuthenticationPrincipal Long userId) {
        tokenService.revokeRefreshToken(userId, req.getRefreshToken());
        return Result.ok("ok");
    }

    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me(@AuthenticationPrincipal Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        return Result.ok(info);
    }

    private LoginResponse toLoginResponse(UserEntity user, TokenService.TokenPair tokens) {
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(tokens.accessToken());
        resp.setRefreshToken(tokens.refreshToken());
        resp.setExpiresIn(tokens.expiresIn());

        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        resp.setUser(info);
        return resp;
    }
}
