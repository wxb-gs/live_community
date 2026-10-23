package com.example.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

public class Outbox {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Outbox.class);
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, Object> kafka;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final String table;

  public Outbox(
      JdbcTemplate jdbc,
      KafkaTemplate<String, Object> kafka,
      ObjectMapper mapper,
      PlatformTransactionManager tm,
      String owner) {
    if (!Set.of("note", "auth").contains(owner))
      throw new IllegalArgumentException("Invalid outbox owner");
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(tm);
    this.table = owner + "_outbox";
  }

  public void append(String topic, String key, Object body) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Outbox append requires a transaction");
    try {
      jdbc.update(
          "INSERT INTO "
              + table
              + " (event_id,topic,event_key,payload,created_at) VALUES (?,?,?,?,?)",
          UUID.randomUUID().toString(),
          topic,
          key,
          mapper.writeValueAsString(body),
          System.currentTimeMillis());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize event", e);
    }
  }

  @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
  public void relay() {
    for (int i = 0; i < 100; i++) {
      try {
        Boolean sent =
            tx.execute(
                status -> {
                  var rows =
                      jdbc.queryForList(
                          "SELECT id,topic,event_key,payload FROM "
                              + table
                              + " WHERE sent_at IS NULL ORDER BY id LIMIT 1 FOR UPDATE SKIP"
                              + " LOCKED");
                  if (rows.isEmpty()) return false;
                  var row = rows.get(0);
                  try {
                    kafka
                        .send(
                            row.get("topic").toString(),
                            row.get("event_key").toString(),
                            mapper.readTree(row.get("payload").toString()))
                        .get(5, TimeUnit.SECONDS);
                  } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new IllegalStateException("Outbox delivery failed", e);
                  }
                  jdbc.update(
                      "UPDATE " + table + " SET sent_at=? WHERE id=?",
                      System.currentTimeMillis(),
                      row.get("id"));
                  return true;
                });
        if (!Boolean.TRUE.equals(sent)) break;
      } catch (RuntimeException e) {
        log.error("Outbox {} delivery failed; row retained for retry", table, e);
        break;
      }
    }
  }
}
