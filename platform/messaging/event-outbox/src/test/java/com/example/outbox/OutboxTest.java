package com.example.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxTest {
  JdbcTemplate jdbc;
  Outbox outbox;
  TransactionTemplate tx;
  KafkaTemplate<String, Object> kafka;

  @BeforeEach
  void init() {
    var ds =
        new DriverManagerDataSource("jdbc:h2:mem:outbox;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP ALL OBJECTS");
    jdbc.execute(
        "CREATE TABLE note_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36),topic"
            + " VARCHAR(128),event_key VARCHAR(160),payload TEXT,created_at BIGINT,sent_at"
            + " BIGINT)");
    var tm = new DataSourceTransactionManager(ds);
    tx = new TransactionTemplate(tm);
    kafka = mock(KafkaTemplate.class);
    outbox = new Outbox(jdbc, kafka, new ObjectMapper(), tm, "note");
  }

  @Test
  void appendRequiresBusinessTransaction() {
    assertThrows(IllegalStateException.class, () -> outbox.append("topic", "key", Map.of()));
  }

  @Test
  void databaseRollbackAlsoRollsBackOutbox() {
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  outbox.append("topic", "key", Map.of("id", 1));
                  throw new IllegalStateException();
                }));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM note_outbox", Integer.class));
  }

  @Test
  void kafkaFailureLeavesEventForNextRelay() {
    tx.executeWithoutResult(s -> outbox.append("topic", "key", Map.of("id", 1)));
    when(kafka.send(eq("topic"), eq("key"), any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));
    outbox.relay();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM note_outbox WHERE sent_at IS NULL", Integer.class));
    when(kafka.send(eq("topic"), eq("key"), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    outbox.relay();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM note_outbox WHERE sent_at IS NULL", Integer.class));
    verify(kafka, times(2)).send(eq("topic"), eq("key"), any());
  }
}
