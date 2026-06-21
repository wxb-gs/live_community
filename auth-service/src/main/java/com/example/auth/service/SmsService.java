package com.example.auth.service;

import com.example.auth.config.SmsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final SmsConfig smsConfig;
    private final RedisTemplate<String, String> redis;
    private final SecureRandom random = new SecureRandom();

    public SmsService(SmsConfig smsConfig, RedisTemplate<String, String> redis) {
        this.smsConfig = smsConfig;
        this.redis = redis;
    }

    public void sendCode(String phone) {
        String code = generateCode();
        String key = "sms_code:" + phone;
        redis.opsForValue().set(key, code, Duration.ofSeconds(smsConfig.getCodeTtl()));

        if ("mock".equals(smsConfig.getProvider())) {
            log.info("[SMS MOCK] code={} sent to phone={}", code, phone);
            return;
        }

        // TODO: integrate real SMS provider (e.g. Aliyun SMS, Tencent Cloud SMS)
        log.info("SMS code sent to {}", phone);
    }

    public boolean verifyCode(String phone, String code) {
        String key = "sms_code:" + phone;
        String stored = redis.opsForValue().get(key);
        if (stored == null) return false;
        if (!stored.equals(code)) return false;
        redis.delete(key);
        return true;
    }

    private String generateCode() {
        int len = smsConfig.getCodeLength();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
