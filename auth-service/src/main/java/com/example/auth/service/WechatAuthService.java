package com.example.auth.service;

import com.example.auth.config.WechatConfig;
import com.example.auth.dto.WechatUserInfo;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserWechatEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserWechatRepository;
import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);

    private final WechatConfig wechatConfig;
    private final UserRepository userRepository;
    private final UserWechatRepository wechatRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public WechatAuthService(WechatConfig wechatConfig, UserRepository userRepository,
                              UserWechatRepository wechatRepository) {
        this.wechatConfig = wechatConfig;
        this.userRepository = userRepository;
        this.wechatRepository = wechatRepository;
    }

    public String buildAuthUrl(String redirectUri) {
        return String.format(
            "https://open.weixin.qq.com/connect/qrconnect?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_login&state=STATE#wechat_redirect",
            wechatConfig.getAppId(), redirectUri
        );
    }

    public UserEntity authenticateAndRegister(String code) {
        if (wechatConfig.getAppId().isEmpty()) {
            throw new RuntimeException("微信登录未配置 (wechat.app-id)");
        }

        WechatUserInfo wechatUser = fetchWechatUserInfo(code);
        var existing = wechatRepository.findByOpenid(wechatUser.getOpenid());

        if (existing.isPresent()) {
            return userRepository.findById(existing.get().getUserId())
                    .orElseThrow(() -> new RuntimeException("用户数据异常"));
        }

        long now = System.currentTimeMillis();
        IdResponse idResp = leafRpcService.generateSegmentId("user");
        long userId = idResp.getId();

        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(null);
        user.setPassword(null);
        user.setNickname(wechatUser.getNickname());
        user.setAvatar(wechatUser.getHeadimgurl());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);

        IdResponse wechatIdResp = leafRpcService.generateSegmentId("wechat");
        UserWechatEntity wechatEntity = new UserWechatEntity();
        wechatEntity.setWechatId(wechatIdResp.getId());
        wechatEntity.setUserId(userId);
        wechatEntity.setOpenid(wechatUser.getOpenid());
        wechatEntity.setUnionid(wechatUser.getUnionid());
        wechatEntity.setNickname(wechatUser.getNickname());
        wechatEntity.setAvatar(wechatUser.getHeadimgurl());
        wechatEntity.setCreatedAt(now);
        wechatRepository.insert(wechatEntity);

        log.info("Wechat auto-registered: userId={}, openid={}", userId, wechatUser.getOpenid());
        return user;
    }

    private WechatUserInfo fetchWechatUserInfo(String code) {
        try {
            String tokenUrl = String.format(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                wechatConfig.getAppId(), wechatConfig.getAppSecret(), code
            );
            String tokenResp = restTemplate.getForObject(tokenUrl, String.class);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);

            if (tokenNode.has("errcode")) {
                throw new RuntimeException("微信授权失败: " + tokenNode.get("errmsg").asText());
            }

            String accessToken = tokenNode.get("access_token").asText();
            String openid = tokenNode.get("openid").asText();

            String userUrl = String.format(
                "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s",
                accessToken, openid
            );
            String userResp = restTemplate.getForObject(userUrl, String.class);
            return objectMapper.readValue(userResp, WechatUserInfo.class);
        } catch (Exception e) {
            log.error("Failed to fetch WeChat user info", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage(), e);
        }
    }
}
