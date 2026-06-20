package com.example.note.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;

public class CassandraKeyspaceInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(CassandraKeyspaceInitializer.class);

    private final String contactPoints;
    private final int port;
    private final String localDatacenter;
    private final String keyspace;

    public CassandraKeyspaceInitializer(String contactPoints, int port, String localDatacenter, String keyspace) {
        this.contactPoints = contactPoints;
        this.port = port;
        this.localDatacenter = localDatacenter;
        this.keyspace = keyspace;
    }

    @Override
    public void afterPropertiesSet() {
        try (CqlSession initSession = new CqlSessionBuilder()
                .addContactPoint(new InetSocketAddress(contactPoints, port))
                .withLocalDatacenter(localDatacenter)
                .build()) {

            initSession.execute(
                    "CREATE KEYSPACE IF NOT EXISTS " + keyspace
                    + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"
            );
            log.info("Keyspace '{}' ensured", keyspace);
        } catch (Exception e) {
            log.warn("Keyspace init warning (may already exist): {}", e.getMessage());
        }
    }
}
