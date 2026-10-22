package com.example.note.content.application;

import com.example.note.content.infrastructure.NoteMysqlRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionEventConsumer {
  private final NoteMysqlRepository notes;
  private final ObjectMapper mapper;

  public InteractionEventConsumer(NoteMysqlRepository notes, ObjectMapper mapper) {
    this.notes = notes;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "interaction_state", groupId = "note-like-projection")
  @Transactional
  public void consume(String raw) throws Exception {
    var e = mapper.readTree(raw);
    if (e.path("schemaVersion").asInt() != 1)
      throw new IllegalArgumentException("Unknown interaction schema");
    if ("note".equals(e.path("targetType").asText())
        && "LIKE".equals(e.path("interactionType").asText()))
      notes.setLikeProjection(
          e.path("targetId").asLong(), e.path("count").asLong(), e.path("version").asLong());
  }
}
