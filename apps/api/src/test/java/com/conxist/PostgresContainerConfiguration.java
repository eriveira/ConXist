package com.conxist;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL in a container. Spring owns its lifecycle, so the database stops only after the application
 * context (and the event registry) has shut down.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {

    static final String IMAGE = "postgres:16-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
