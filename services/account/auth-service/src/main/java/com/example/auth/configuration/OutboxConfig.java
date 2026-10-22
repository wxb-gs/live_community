package com.example.auth.configuration;

import com.example.outbox.Outbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class OutboxConfig {
  @Bean
  Outbox outbox(
      JdbcTemplate jdbc,
      KafkaTemplate<String, Object> kafka,
      ObjectMapper mapper,
      PlatformTransactionManager tm) {
    return new Outbox(jdbc, kafka, mapper, tm, "auth");
  }
}
