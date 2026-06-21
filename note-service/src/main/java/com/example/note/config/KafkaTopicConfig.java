package com.example.note.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_INTERACTION = "interaction-events";

    @Bean
    public NewTopic interactionTopic() {
        return TopicBuilder.name(TOPIC_INTERACTION)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
