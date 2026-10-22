package com.example.note;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.*;
import com.example.note.access.TargetAccess;
import com.example.note.content.application.NoteSyncProducer;
import com.example.note.content.infrastructure.NoteMysqlRepository;
import com.example.note.interaction.application.InteractionService;
import com.example.outbox.Outbox;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class BoundaryPersistenceTest {
  AnnotationConfigApplicationContext context;
  JdbcTemplate jdbc;
  InteractionService interactions;
  NoteMysqlRepository notes;
  Outbox outbox;

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean
    javax.sql.DataSource ds() {
      return new DriverManagerDataSource(
          "jdbc:h2:mem:boundary;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Bean
    JdbcTemplate jdbc(javax.sql.DataSource ds) {
      return new JdbcTemplate(ds);
    }

    @Bean
    PlatformTransactionManager tm(javax.sql.DataSource ds) {
      return new DataSourceTransactionManager(ds);
    }

    @Bean
    Outbox outbox() {
      return mock(Outbox.class);
    }

    @Bean
    TargetAccess access() {
      return mock(TargetAccess.class);
    }

    @Bean
    InteractionService interactions(JdbcTemplate j, Outbox o, TargetAccess a) {
      return new InteractionService(j, o, a);
    }

    @Bean
    NoteSyncProducer sync(Outbox o) {
      return new NoteSyncProducer(o);
    }

    @Bean
    NoteMysqlRepository notes(JdbcTemplate j, NoteSyncProducer s) {
      return new NoteMysqlRepository(j, s);
    }
  }

  @BeforeEach
  void setup() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    outbox = context.getBean(Outbox.class);
    interactions = context.getBean(InteractionService.class);
    notes = context.getBean(NoteMysqlRepository.class);
    jdbc.execute("DROP ALL OBJECTS");
    jdbc.execute(
        "CREATE TABLE interaction_record(target_type VARCHAR(32),target_id BIGINT,interaction_type"
            + " VARCHAR(32),user_id BIGINT,status VARCHAR(16),created_at BIGINT,updated_at"
            + " BIGINT,PRIMARY KEY(target_type,target_id,interaction_type,user_id))");
    jdbc.execute(
        "CREATE TABLE interaction_count(target_type VARCHAR(32),target_id BIGINT,interaction_type"
            + " VARCHAR(32),total BIGINT,version BIGINT,PRIMARY"
            + " KEY(target_type,target_id,interaction_type))");
    jdbc.execute(
        "CREATE TABLE interaction_command(user_id BIGINT,request_id VARCHAR(64),fingerprint"
            + " VARCHAR(160),active TINYINT,total BIGINT,PRIMARY KEY(user_id,request_id))");
    jdbc.execute(
        "CREATE TABLE note(id BIGINT PRIMARY KEY,user_id BIGINT,title VARCHAR(256),content"
            + " TEXT,summary VARCHAR(512),tags VARCHAR(512),category VARCHAR(64),object_key"
            + " VARCHAR(512),file_id VARCHAR(36),view_count BIGINT,like_count BIGINT,status"
            + " VARCHAR(16),created_at BIGINT,updated_at BIGINT,version BIGINT,like_version BIGINT"
            + " DEFAULT 0)");
    jdbc.execute(
        "CREATE TABLE note_view(event_id VARCHAR(64) PRIMARY KEY,note_id BIGINT,user_id"
            + " BIGINT,created_at BIGINT,applied TINYINT DEFAULT 0)");
  }

  @AfterEach
  void close() {
    context.close();
  }

  ToggleRequest command(boolean active, String request) {
    var r = new ToggleRequest("LIKE", "note", 10L, 7L);
    r.setDesiredActive(active);
    r.setRequestId(request);
    return r;
  }

  @Test
  void repeatedSetAndCancellationProduceCorrectNetCount() {
    assertEquals(1, interactions.set(command(true, "a")).count());
    assertEquals(1, interactions.set(command(true, "a")).count());
    assertEquals(1, interactions.set(command(true, "b")).count());
    assertEquals(0, interactions.set(command(false, "c")).count());
    verify(outbox, times(2)).append(eq("interaction_state"), anyString(), any());
  }

  @Test
  void idempotencyKeyCannotChangeMeaning() {
    interactions.set(command(true, "same"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> interactions.set(command(false, "same")));
    assertEquals(1, interactions.getCount(InteractionType.LIKE, "note", 10L));
  }

  @Test
  void outboxFailureRollsBackRelationshipAndCount() {
    doThrow(new IllegalStateException("disk failure"))
        .when(outbox)
        .append(anyString(), anyString(), any());
    assertThrows(IllegalStateException.class, () -> interactions.set(command(true, "a")));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interaction_record", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interaction_count", Integer.class));
  }

  @Test
  void commentRelationshipsAreDurable() {
    var req = command(true, "c");
    req.setTargetType("comment");
    interactions.set(req);
    assertEquals(1, interactions.getStatus(InteractionType.LIKE, "comment", 10L, 7L).count());
  }

  void draft() {
    notes.upsert(10, 7, "title", "body", "summary", null, null, null, "DRAFT", 1, 1);
    clearInvocations(outbox);
  }

  @Test
  void authorRequiredAndPublishedSnapshotIsReturned() {
    draft();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> notes.publish(10, 8, "file", "uploads:ready/file"));
    var row = notes.publish(10, 7, "file", "uploads:ready/file");
    assertEquals("PUBLISHED", row.getStatus());
    assertEquals("file", row.getFileId());
    assertEquals(2, row.getVersion());
    assertEquals(2, notes.publish(10, 7, "file", "uploads:ready/file").getVersion());
    verify(outbox, times(1)).append(eq("search_documents"), anyString(), any());
  }

  @Test
  void staleLikeProjectionDoesNotOverwriteNewerCount() {
    draft();
    notes.setLikeProjection(10, 8, 3);
    notes.setLikeProjection(10, 2, 2);
    notes.setLikeProjection(10, 8, 3);
    assertEquals(8, notes.findById(10).orElseThrow().getLikeCount());
    verify(outbox, times(1)).append(eq("search_documents"), anyString(), any());
  }

  @Test
  void successfulViewIsDurableAndReplayDoesNotDoubleCount() {
    draft();
    notes.publish(10, 7, "file", "uploads:ready/file");
    notes.recordView(10, 7, "view-1");
    notes.recordView(10, 7, "view-1");
    assertEquals(1, notes.findById(10).orElseThrow().getViewCount());
    assertThrows(IllegalArgumentException.class, () -> notes.recordView(10, 8, "view-1"));
  }

  @Test
  void viewOutboxFailureRollsBackCountAndJournal() {
    draft();
    notes.publish(10, 7, "file", "uploads:ready/file");
    doThrow(new IllegalStateException("disk failure"))
        .when(outbox)
        .append(anyString(), anyString(), any());
    assertThrows(IllegalStateException.class, () -> notes.recordView(10, 7, "v"));
    assertEquals(0, notes.findById(10).orElseThrow().getViewCount());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM note_view", Integer.class));
  }

  @Test
  void draftDoesNotCountAsPublicView() {
    draft();
    notes.recordView(10, 7, "v");
    assertEquals(0, notes.findById(10).orElseThrow().getViewCount());
  }

  @Test
  void concurrentUsersDoNotLoseCounts() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
      for (long user = 1; user <= 12; user++) {
        long id = user;
        futures.add(
            pool.submit(
                () -> {
                  var req = command(true, "user-" + id);
                  req.setUserId(id);
                  interactions.set(req);
                }));
      }
      for (var f : futures) f.get();
      assertEquals(12, interactions.getCount(InteractionType.LIKE, "note", 10L));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentRepeatedDesiredStateForOneUserOnlyCountsOnce() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
      for (int i = 0; i < 12; i++) {
        String request = "same-user-" + i;
        jobs.add(pool.submit(() -> interactions.set(command(true, request))));
      }
      for (var job : jobs) job.get();
      assertEquals(1, interactions.getCount(InteractionType.LIKE, "note", 10L));
      verify(outbox, times(1)).append(eq("interaction_state"), anyString(), any());
    } finally {
      pool.shutdownNow();
    }
  }
}
