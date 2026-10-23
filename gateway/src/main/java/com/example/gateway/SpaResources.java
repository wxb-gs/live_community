package com.example.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.*;
import org.springframework.web.reactive.config.*;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

@Configuration
public class SpaResources implements WebFluxConfigurer {
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Mono<Resource> getResource(String path, Resource location) {
                if (path.startsWith("api/")) return Mono.empty();
                return super.getResource(path, location)
                    .switchIfEmpty(
                        Mono.defer(
                            () ->
                                path.startsWith("assets/") || path.contains(".")
                                    ? Mono.empty()
                                    : Mono.just(new ClassPathResource("static/index.html"))));
              }
            });
  }
}
