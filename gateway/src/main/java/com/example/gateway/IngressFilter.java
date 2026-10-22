package com.example.gateway;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IngressFilter implements GlobalFilter, Ordered {
  private final int limit;
  private long second;
  private int used;

  public IngressFilter(@Value("${gateway.requests-per-second:100}") int limit) {
    this.limit = limit;
  }

  private synchronized boolean allow() {
    long now = System.currentTimeMillis() / 1000;
    if (second != now) {
      second = now;
      used = 0;
    }
    return ++used <= limit;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!allow()) {
      exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
      return exchange.getResponse().setComplete();
    }
    String trace = UUID.randomUUID().toString();
    var request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  h.remove("X-User-Id");
                  h.remove("X-Actor-Id");
                  h.remove("X-Username");
                  h.set("X-Request-Id", trace);
                })
            .build();
    exchange.getResponse().getHeaders().set("X-Request-Id", trace);
    return chain.filter(exchange.mutate().request(request).build());
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
