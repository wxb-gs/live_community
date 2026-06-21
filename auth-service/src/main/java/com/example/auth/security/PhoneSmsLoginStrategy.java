package com.example.auth.security;

import com.example.auth.dto.LoginRequest;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserPhoneEntity;
import com.example.auth.repository.UserPhoneRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.SmsService;
import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PhoneSmsLoginStrategy extends AbstractLoginStrategy {

    private static final Logger log = LoggerFactory.getLogger(PhoneSmsLoginStrategy.class);

    private final SmsService smsService;
    private final UserRepository userRepository;
    private final UserPhoneRepository phoneRepository;

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public PhoneSmsLoginStrategy(SmsService smsService, UserRepository userRepository,
                                 UserPhoneRepository phoneRepository) {
        this.smsService = smsService;
        this.userRepository = userRepository;
        this.phoneRepository = phoneRepository;
    }

    @Override
    public LoginType getLoginType() { return LoginType.PHONE; }

    @Override
    protected UserEntity doAuthenticate(LoginRequest req) {
        if (!smsService.verifyCode(req.getPhone(), req.getSmsCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        var existing = phoneRepository.findByPhone(req.getPhone());
        if (existing.isPresent()) {
            return userRepository.findById(existing.get().getUserId())
                    .orElseThrow(() -> new RuntimeException("用户数据异常"));
        }

        // Auto-register: first-time phone login
        long now = System.currentTimeMillis();
        IdResponse idResp = leafRpcService.generateSegmentId("user");
        long userId = idResp.getId();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(null);
        user.setPassword(null);
        user.setNickname("用户" + req.getPhone().substring(req.getPhone().length() - 4));
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);

        IdResponse phoneIdResp = leafRpcService.generateSegmentId("phone");
        UserPhoneEntity phoneEntity = new UserPhoneEntity();
        phoneEntity.setPhoneId(phoneIdResp.getId());
        phoneEntity.setUserId(userId);
        phoneEntity.setPhone(req.getPhone());
        phoneEntity.setCreatedAt(now);
        phoneRepository.insert(phoneEntity);

        log.info("Phone auto-registered: userId={}, phone={}", userId, req.getPhone());
        return user;
    }
}
