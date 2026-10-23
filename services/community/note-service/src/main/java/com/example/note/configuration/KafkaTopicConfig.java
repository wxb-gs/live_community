package com.example.note.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  public static final String TOPIC_INTERACTION = "interaction_state";
  public static final String TOPIC_SEARCH_SYNC = "search_documents";

  @Bean
  public NewTopic interactionTopic() {
    return TopicBuilder.name(TOPIC_INTERACTION).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic searchSyncTopic() {
    return TopicBuilder.name(TOPIC_SEARCH_SYNC).partitions(3).replicas(1).build();
  }
}
