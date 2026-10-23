package com.example.searchsync.configuration;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String servers) {
    Map<String, Object> p = new HashMap<>();
    p.put("bootstrap.servers", servers);
    p.put("enable.auto.commit", false);
    p.put("auto.offset.reset", "earliest");
    p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    p.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(p));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    // Unlimited retry: a failed record stays uncommitted and is visible as consumer lag.
    // Repair/replay tooling can resolve poison messages; never silently skip.
    var error = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    error.setClassifications(Map.of(), true);
    factory.setCommonErrorHandler(error);
    return factory;
  }
}
