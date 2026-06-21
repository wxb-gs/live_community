package com.example.note.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.cql.CqlTemplate;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

@Configuration
@EnableCassandraRepositories(basePackages = "com.example.note.repository")
public class CassandraConfig {

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
    public CqlTemplate cqlTemplate(CqlSession session) {
        return new CqlTemplate(session);
    }
}
