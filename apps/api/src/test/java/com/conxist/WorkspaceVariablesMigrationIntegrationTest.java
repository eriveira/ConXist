package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import com.conxist.testing.FakePlaudConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V19 turns each workspace's topics into one "topics" variable and keeps the old table, renamed. It runs here in a
 * schema of its own on one connection, after V13, so the tables the app uses are not touched.
 */
@SpringBootTest
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class WorkspaceVariablesMigrationIntegrationTest {

    private static final String WITH_TOPICS = "11111111-1111-1111-1111-111111111111";
    private static final String MANY_TOPICS = "22222222-2222-2222-2222-222222222222";
    private static final String NO_TOPICS = "33333333-3333-3333-3333-333333333333";

    @Autowired
    JdbcTemplate sql;

    @Test
    void topics_become_one_variable_per_workspace_and_the_old_table_is_kept() {
        Map<String, String> values = sql.execute((ConnectionCallback<Map<String, String>>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create schema v19_check");
                statement.execute("set search_path to v19_check");
                statement.execute("create table workspaces (id uuid primary key)");
                statement.execute("insert into workspaces values ('" + WITH_TOPICS + "'), ('" + MANY_TOPICS + "'), ('"
                        + NO_TOPICS + "')");
                run(statement, "V13__workspace_topics.sql");
                statement.execute("insert into workspace_topics values (gen_random_uuid(), '" + WITH_TOPICS
                        + "', 'Payments', 'Billing and refunds', now()), (gen_random_uuid(), '" + WITH_TOPICS
                        + "', 'Checkout', '', now())");
                statement.execute("insert into workspace_topics select gen_random_uuid(), '" + MANY_TOPICS
                        + "', 'Topic ' || n, repeat('d', 300), now() from generate_series(1, 50) n");
                run(statement, "V19__workspace_variables.sql");
                return read(connection);
            } finally {
                try (Statement cleanup = connection.createStatement()) {
                    cleanup.execute("set search_path to public");
                    cleanup.execute("drop schema v19_check cascade");
                }
            }
        });

        assertThat(values).containsEntry(WITH_TOPICS + "/topics", "Checkout; Payments (Billing and refunds)");
        assertThat(values.get(MANY_TOPICS + "/topics")).hasSize(2_000).startsWith("Topic 1 (ddd");
        assertThat(values).doesNotContainKey(NO_TOPICS + "/topics");
        assertThat(values).containsEntry("kept", "52");
    }

    private static void run(Statement statement, String migration) throws SQLException {
        try {
            String text = new ClassPathResource("db/migration/" + migration).getContentAsString(StandardCharsets.UTF_8);
            for (String part : text.replaceAll("(?m)^--.*$", "").split(";\\s*(\\n|$)")) {
                if (!part.isBlank()) {
                    statement.execute(part);
                }
            }
        } catch (IOException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static Map<String, String> read(Connection connection) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("select workspace_id, name, value from workspace_variables")) {
                while (rows.next()) {
                    values.put(rows.getString(1) + "/" + rows.getString(2), rows.getString(3));
                }
            }
            try (ResultSet kept = statement.executeQuery("select count(*) from workspace_topics_v13")) {
                kept.next();
                values.put("kept", kept.getString(1));
            }
        }
        return values;
    }
}
