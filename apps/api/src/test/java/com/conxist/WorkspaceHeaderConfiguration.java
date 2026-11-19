package com.conxist;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Every MockMvc request names the test workspace, as the app does; {@link #ensure} makes sure it exists. */
@TestConfiguration(proxyBeanMethods = false)
class WorkspaceHeaderConfiguration {

    static final String HEADER = "X-Workspace-Id";
    static final UUID TEST_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Bean
    MockMvcBuilderCustomizer workspaceHeader() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").header(HEADER, TEST_WORKSPACE));
    }

    static void ensure(JdbcClient jdbc) {
        jdbc.sql("""
                insert into workspaces (id, name, created_at) values (:id, 'Test workspace', now())
                on conflict do nothing
                """).param("id", TEST_WORKSPACE).update();
    }
}
