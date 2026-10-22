package com.example.upload.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.common.*;
import com.example.security.ServiceSecurity;
import com.example.upload.service.FileService;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UploadController.class,
    properties = {
      "jwt.secret=test-secret-for-http-authentication",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.enabled=false",
      "spring.config.import="
    })
@org.springframework.test.context.ContextConfiguration(
    classes = {UploadController.class, ServiceSecurity.class})
class UploadControllerTest {
  @Autowired MockMvc mvc;
  @MockBean FileService files;

  String token() {
    var key =
        new SecretKeySpec(
            Base64.getEncoder()
                .encode("test-secret-for-http-authentication".getBytes(StandardCharsets.UTF_8)),
            "HmacSHA256");
    return "Bearer "
        + Jwts.builder()
            .subject("7")
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(key)
            .compact();
  }

  @Test
  void directHttpRequiresToken() throws Exception {
    mvc.perform(
            post("/api/upload/prepare")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(files);
  }

  @Test
  void actorComesFromVerifiedToken() throws Exception {
    when(files.prepare(any())).thenReturn(new PresignedUrlResponse("url", "key", 1));
    mvc.perform(
            post("/api/upload/prepare")
                .header("Authorization", token())
                .contentType("application/json")
                .content(
                    "{\"userId\":999,\"noteId\":10,\"size\":128,\"contentType\":\"image/png\"}"))
        .andExpect(status().isOk());
    verify(files).prepare(argThat(r -> r.getUserId() == 7));
  }

  @Test
  void oldPrepareEndpointIsGone() throws Exception {
    mvc.perform(get("/api/upload/presigned").header("Authorization", token()))
        .andExpect(status().isGone());
  }
}
