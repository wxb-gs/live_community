package com.example.auth.config;

import com.example.leafclient.LeafIdClient;
import org.springframework.context.annotation.*;

@Configuration
public class LeafClientConfig {
  @Bean
  LeafIdClient leafIdClient() {
    return new LeafIdClient();
  }
}
