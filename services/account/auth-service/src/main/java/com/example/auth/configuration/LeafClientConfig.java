package com.example.auth.configuration;

import com.example.leafclient.LeafIdClient;
import org.springframework.context.annotation.*;

@Configuration
public class LeafClientConfig {
  @Bean
  LeafIdClient leafIdClient() {
    return new LeafIdClient();
  }
}
