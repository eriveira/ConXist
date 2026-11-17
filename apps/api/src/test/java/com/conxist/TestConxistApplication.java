package com.conxist;

import org.springframework.boot.SpringApplication;

/** Runs the API locally against a throwaway PostgreSQL container: {@code mvn spring-boot:test-run}. */
public class TestConxistApplication {

    public static void main(String[] args) {
        SpringApplication.from(ConxistApplication::main).with(PostgresContainerConfiguration.class).run(args);
    }
}
