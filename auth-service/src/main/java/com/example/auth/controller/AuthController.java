package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.LoginStrategy;
import com.example.auth.security.LoginStrategyFactory;
import com.example.auth.security.LoginType;
import com.example.auth.service.SmsService;
import com.example.auth.service.TaobaoAuthService;
import com.example.auth.service.TokenService;
import com.example.auth.service.WechatAuthService;
import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import com.example.common.Result;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginStrategyFactory strategyFactory;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WechatAuthService wechatAuthService;
    private final TaobaoAuthService taobaoAuthService;
    private final SmsService smsService;

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public AuthController(LoginStrategyFactory strategyFactory, TokenService tokenService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder,
                          WechatAuthService wechatAuthService, TaobaoAuthService taobaoAuthService,
                          SmsService smsService) {
        this.strategyFactory = strategyFactory;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.wechatAuthService = wechatAuthService;
        this.taobaoAuthService = taobaoAuthService;
        this.smsService = smsService;
    }

    // ── Unified login (Strategy pattern dispatch) ────────────────────────

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        LoginType type = req.getType() != null
                ? LoginType.fromValue(req.getType())
                : LoginType.PASSWORD; // backward compatible: default to password

        LoginStrategy strategy = strategyFactory.getStrategy(type);
        UserEntity user = strategy.authenticate(req);

        TokenService.TokenPair tokens = tokenService.issueTokens(user);
        return Result.ok(toLoginResponse(user, tokens));
    }

    // ── Register ─────────────────────────────────────────────────────────

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        long now = System.currentTimeMillis();
        IdResponse idResp = leafRpcService.generateSegmentId("user");
        long userId = idResp.getId();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setType(LoginType.PASSWORD.getValue());
        loginReq.setUsername(req.getUsername());
        loginReq.setPassword(req.getPassword());
        return login(loginReq);
    }

    // ── WeChat ────────────────────────────────────────────────────────────

    @GetMapping("/wechat/url")
    public Result<String> wechatUrl(@RequestParam String redirectUri) {
        return Result.ok(wechatAuthService.buildAuthUrl(redirectUri));
    }

    // ── Taobao ────────────────────────────────────────────────────────────

    @GetMapping("/taobao/url")
    public Result<String> taobaoUrl(@RequestParam String redirectUri) {
        return Result.ok(taobaoAuthService.buildAuthUrl(redirectUri));
    }

    // ── Phone SMS ─────────────────────────────────────────────────────────

    @PostMapping("/phone/send-code")
    public Result<String> sendSmsCode(@RequestBody SmsSendRequest req) {
        smsService.sendCode(req.getPhone());
        return Result.ok("验证码已发送");
    }

    // ── Token management ──────────────────────────────────────────────────

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

    // ── Private helpers ───────────────────────────────────────────────────

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
