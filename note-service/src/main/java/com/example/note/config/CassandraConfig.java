package com.example.note.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.CassandraAdminTemplate;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.net.InetSocketAddress;

@Configuration
@EnableCassandraRepositories(basePackages = "com.example.note.repository")
public class CassandraConfig {

    private static final Logger log = LoggerFactory.getLogger(CassandraConfig.class);

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    @Value("${spring.cassandra.local-datacenter}")
    private String localDatacenter;

    @Bean
    public CassandraKeyspaceInitializer cassandraKeyspaceInitializer() {
        return new CassandraKeyspaceInitializer(contactPoints, port, localDatacenter, keyspace);
    }

    @Bean
    public CqlSession cqlSession(CassandraKeyspaceInitializer init) {
        CqlSession session = new CqlSessionBuilder()
                .addContactPoint(new InetSocketAddress(contactPoints, port))
                .withLocalDatacenter(localDatacenter)
                .withKeyspace(keyspace)
                .build();
        log.info("Cassandra CqlSession connected to keyspace '{}'", keyspace);
        return session;
    }

    @Bean
    public CqlTemplate cqlTemplate(CqlSession session) {
        return new CqlTemplate(session);
    }

    @Bean
    public CassandraTemplate cassandraTemplate(CqlSession session) {
        return new CassandraTemplate(session);
    }

    @Bean
    public CassandraAdminTemplate cassandraAdminTemplate(CqlSession session) {
        return new CassandraAdminTemplate(session);
    }
}
