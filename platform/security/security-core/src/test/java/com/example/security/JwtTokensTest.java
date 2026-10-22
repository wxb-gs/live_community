package com.example.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtTokensTest {
  String secret = "unit-test-secret-long-enough";

  String token(String subject, Date expires) {
    var k =
        new SecretKeySpec(
            Base64.getEncoder().encode(secret.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    var b = Jwts.builder().subject(subject);
    if (expires != null) b.expiration(expires);
    return "Bearer " + b.signWith(k).compact();
  }

  @Test
  void validSubject() {
    assertEquals(
        7, JwtTokens.userId(secret, token("7", new Date(System.currentTimeMillis() + 60000))));
  }

  @Test
  void expirationIsRequired() {
    assertThrows(RuntimeException.class, () -> JwtTokens.userId(secret, token("7", null)));
  }

  @Test
  void expiredAndMalformedSubjectsAreRejected() {
    assertThrows(RuntimeException.class, () -> JwtTokens.userId(secret, token("7", new Date(1))));
    assertThrows(
        RuntimeException.class,
        () ->
            JwtTokens.userId(
                secret, token("not-a-user", new Date(System.currentTimeMillis() + 60000))));
  }

  @Test
  void forgedOrMissingTokenIsRejected() {
    assertThrows(RuntimeException.class, () -> JwtTokens.userId(secret, "Bearer fake"));
    assertThrows(RuntimeException.class, () -> JwtTokens.userId(secret, null));
  }
}
