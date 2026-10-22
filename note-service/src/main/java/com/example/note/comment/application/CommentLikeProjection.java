package com.example.note.comment.application;

import com.example.note.comment.infrastructure.CommentIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentLikeProjection {
  private final CommentIndexRepository comments;
  private final ObjectMapper mapper;

  public CommentLikeProjection(CommentIndexRepository comments, ObjectMapper mapper) {
    this.comments = comments;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "interaction_state", groupId = "comment-like-projection")
  @Transactional
  public void consume(String raw) throws Exception {
    var e = mapper.readTree(raw);
    if (e.path("schemaVersion").asInt() != 1)
      throw new IllegalArgumentException("Unknown interaction schema");
    if ("comment".equals(e.path("targetType").asText())
        && "LIKE".equals(e.path("interactionType").asText()))
      comments.setLikeProjection(
          e.path("targetId").asLong(), e.path("count").asLong(), e.path("version").asLong());
  }
}
