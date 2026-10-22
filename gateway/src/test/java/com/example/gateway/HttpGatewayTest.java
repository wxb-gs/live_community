package com.example.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {GatewayApplication.class, HttpGatewayTest.Routes.class},
    properties = {
      "spring.config.import=",
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "jwt.secret=test-secret-for-http-authentication",
      "gateway.requests-per-second=10000"
    })
class HttpGatewayTest {
  static HttpServer downstream;
  static AtomicInteger calls = new AtomicInteger();

  static {
    try {
      downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      downstream.createContext(
          "/",
          e -> {
            calls.incrementAndGet();
            byte[] body = e.getRequestBody().readAllBytes();
            boolean forged = e.getRequestHeaders().containsKey("X-User-Id");
            String response =
                e.getRequestURI()
                    + "|"
                    + new String(body, StandardCharsets.UTF_8)
                    + "|forged="
                    + forged
                    + "|auth="
                    + (e.getRequestHeaders().getFirst("Authorization") != null);
            byte[] b = response.getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(
                e.getRequestURI().getPath().endsWith("conflict") ? 409 : 200, b.length);
            e.getResponseBody().write(b);
            e.close();
          });
      downstream.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @TestConfiguration
  static class Routes {
    @Bean
    RouteLocator localRoutes(RouteLocatorBuilder b) {
      return b.routes()
          .route(
              "local-test",
              r ->
                  r.order(-10)
                      .path("/api/note/**")
                      .uri("http://127.0.0.1:" + downstream.getAddress().getPort()))
          .build();
    }
  }

  @Autowired WebTestClient client;

  String token() {
    var k =
        new SecretKeySpec(
            Base64.getEncoder()
                .encode("test-secret-for-http-authentication".getBytes(StandardCharsets.UTF_8)),
            "HmacSHA256");
    return "Bearer "
        + Jwts.builder()
            .subject("7")
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(k)
            .compact();
  }

  @AfterAll
  static void close() {
    downstream.stop(0);
  }

  @Test
  void unauthorizedRequestNeverReachesHttpService() {
    int before = calls.get();
    client
        .get()
        .uri("/api/note/detail")
        .header("X-User-Id", "7")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    assertEquals(before, calls.get());
  }

  @Test
  void routesHttpBodyQueryAndTokenAndStripsForgedIdentity() {
    client
        .post()
        .uri("/api/note/draft?sample=yes")
        .header("Authorization", token())
        .header("X-User-Id", "999")
        .bodyValue("payload")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists("X-Request-Id")
        .expectBody(String.class)
        .isEqualTo("/api/note/draft?sample=yes|payload|forged=false|auth=true");
  }

  @Test
  void downstreamBusinessStatusIsPreservedAndWritesAreNotRetried() {
    int before = calls.get();
    client
        .post()
        .uri("/api/note/conflict")
        .header("Authorization", token())
        .exchange()
        .expectStatus()
        .isEqualTo(409);
    assertEquals(before + 1, calls.get());
  }

  @Test
  void corsPreflightIsAllowed() {
    client
        .options()
        .uri("/api/note/draft")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,content-type")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
  }
}
