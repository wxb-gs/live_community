package com.example.auth.service;

import com.example.auth.config.TaobaoConfig;
import com.example.auth.dto.TaobaoUserInfo;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserTaobaoEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserTaobaoRepository;
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
public class TaobaoAuthService {

    private static final Logger log = LoggerFactory.getLogger(TaobaoAuthService.class);

    private final TaobaoConfig taobaoConfig;
    private final UserRepository userRepository;
    private final UserTaobaoRepository taobaoRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public TaobaoAuthService(TaobaoConfig taobaoConfig, UserRepository userRepository,
                             UserTaobaoRepository taobaoRepository) {
        this.taobaoConfig = taobaoConfig;
        this.userRepository = userRepository;
        this.taobaoRepository = taobaoRepository;
    }

    public String buildAuthUrl(String redirectUri) {
        return String.format(
            "https://oauth.taobao.com/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=STATE",
            taobaoConfig.getAppKey(), redirectUri
        );
    }

    public UserEntity authenticateAndRegister(String code) {
        if (taobaoConfig.getAppKey().isEmpty()) {
            throw new RuntimeException("淘宝登录未配置 (taobao.app-key)");
        }

        TaobaoUserInfo taobaoUser = fetchTaobaoUserInfo(code);
        var existing = taobaoRepository.findByOpenId(taobaoUser.getOpenId());

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
        user.setNickname(taobaoUser.getNick());
        user.setAvatar(taobaoUser.getAvatar());
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);

        IdResponse taobaoIdResp = leafRpcService.generateSegmentId("taobao");
        UserTaobaoEntity taobaoEntity = new UserTaobaoEntity();
        taobaoEntity.setTaobaoId(taobaoIdResp.getId());
        taobaoEntity.setUserId(userId);
        taobaoEntity.setOpenId(taobaoUser.getOpenId());
        taobaoEntity.setNick(taobaoUser.getNick());
        taobaoEntity.setAvatar(taobaoUser.getAvatar());
        taobaoEntity.setCreatedAt(now);
        taobaoRepository.insert(taobaoEntity);

        log.info("Taobao auto-registered: userId={}, openId={}", userId, taobaoUser.getOpenId());
        return user;
    }

    private TaobaoUserInfo fetchTaobaoUserInfo(String code) {
        try {
            String tokenUrl = String.format(
                "https://oauth.taobao.com/token?client_id=%s&client_secret=%s&code=%s&grant_type=authorization_code",
                taobaoConfig.getAppKey(), taobaoConfig.getAppSecret(), code
            );
            String tokenResp = restTemplate.postForObject(tokenUrl, null, String.class);
            JsonNode tokenNode = objectMapper.readTree(tokenResp);

            if (tokenNode.has("error")) {
                throw new RuntimeException("淘宝授权失败: " + tokenNode.get("error_description").asText());
            }

            String accessToken = tokenNode.get("access_token").asText();
            String openId = tokenNode.get("open_id").asText();

            String userUrl = String.format(
                "https://eco.taobao.com/router/rest?method=taobao.user.get&access_token=%s&open_id=%s",
                accessToken, openId
            );
            String userResp = restTemplate.getForObject(userUrl, String.class);

            JsonNode userNode = objectMapper.readTree(userResp);
            TaobaoUserInfo info = new TaobaoUserInfo();
            info.setOpenId(openId);
            info.setNick(userNode.has("nick") ? userNode.get("nick").asText() : "淘宝用户");
            info.setAvatar(userNode.has("avatar") ? userNode.get("avatar").asText() : "");
            return info;
        } catch (Exception e) {
            log.error("Failed to fetch Taobao user info", e);
            throw new RuntimeException("淘宝登录失败: " + e.getMessage(), e);
        }
    }
}
