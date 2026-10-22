package com.example.security;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

public final class JwtTokens {
  private JwtTokens() {}

  public static long userId(String secret, String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw new IllegalArgumentException("Missing bearer token");
    var key =
        new SecretKeySpec(
            Base64.getEncoder().encode(secret.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    var claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(authorization.substring(7))
            .getPayload();
    if (claims.getExpiration() == null)
      throw new IllegalArgumentException("Token expiration required");
    long id = Long.parseLong(claims.getSubject());
    if (id <= 0) throw new IllegalArgumentException("Invalid subject");
    return id;
  }
}
