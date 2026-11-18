package com.conxist;

import com.conxist.testing.FakePlaudConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the API locally against a throwaway PostgreSQL container and a fake Plaud Team ({@code mvn
 * spring-boot:test-run}). To try the Plaud flow, use the Client ID {@code demo-team}.
 */
public class TestConxistApplication {

    public static void main(String[] args) {
        SpringApplication.from(ConxistApplication::main).with(PostgresContainerConfiguration.class, FakePlaudConfiguration.class).run(args);
    }
}
