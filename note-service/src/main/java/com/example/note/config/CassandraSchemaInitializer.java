package com.example.note.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CassandraSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(CassandraSchemaInitializer.class);

    private final CqlSession cqlSession;

    public CassandraSchemaInitializer(CqlSession cqlSession) {
        this.cqlSession = cqlSession;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSchema() {
        try {
            cqlSession.execute("""
                CREATE TABLE IF NOT EXISTS note (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT,
                    title TEXT,
                    content TEXT,
                    summary TEXT,
                    object_key TEXT,
                    status TEXT,
                    created_at BIGINT,
                    updated_at BIGINT
                )
                """);
            cqlSession.execute("""
                CREATE TABLE IF NOT EXISTS comment (
                    note_id BIGINT,
                    comment_id BIGINT,
                    user_id BIGINT,
                    content TEXT,
                    created_at BIGINT,
                    PRIMARY KEY (note_id, comment_id)
                )
                """);
            cqlSession.execute("CREATE INDEX IF NOT EXISTS ON note (user_id)");
            cqlSession.execute("CREATE INDEX IF NOT EXISTS ON note (status)");
            cqlSession.execute("""
                CREATE TABLE IF NOT EXISTS comment_like (
                    comment_id BIGINT,
                    user_id BIGINT,
                    status TEXT,
                    created_at BIGINT,
                    PRIMARY KEY (comment_id, user_id)
                )
                """);
            cqlSession.execute("""
                CREATE TABLE IF NOT EXISTS comment_like_count (
                    comment_id BIGINT PRIMARY KEY,
                    like_count COUNTER
                )
                """);
            log.info("Cassandra schema ensured: tables note, comment, comment_like, comment_like_count + indexes");
        } catch (Exception e) {
            log.warn("Schema init warning: {}", e.getMessage());
        }
    }
}
