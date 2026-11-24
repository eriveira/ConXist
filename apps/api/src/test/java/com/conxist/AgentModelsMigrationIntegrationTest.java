package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.testing.FakePlaudConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** V14 turns each old setting (fast, balanced, thorough) into the model and effort it ran on, and runs only once. */
@SpringBootTest
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class AgentModelsMigrationIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    /** Runs the migration's statements as written: a plain statement, so the {@code ?} key test stays SQL. */
    @Autowired
    JdbcTemplate sql;

    @Test
    void each_old_setting_becomes_its_model_and_effort() throws IOException {
        UUID fast = agent("FAST");
        UUID balanced = agent("BALANCED");
        UUID thorough = agent("THOROUGH");
        UUID picked = UUID.randomUUID();
        insert(picked, "{\"name\":\"Picked\",\"provider\":\"anthropic\",\"model\":\"claude-opus-5-5\",\"effort\":\"LOW\"}");
        jdbc.sql("insert into agent_versions (agent_id, number, definition, published_at) "
                + "values (?, 1, cast(? as jsonb), now())")
                .params(thorough, "{\"name\":\"Old v1\",\"model\":\"THOROUGH\"}").update();

        String v14 = new ClassPathResource("db/migration/V14__agent_models.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String statement : v14.replaceAll("(?m)^--.*$", "").split(";")) {
            if (!statement.isBlank()) {
                sql.execute(statement);
            }
        }

        assertThat(model(fast)).containsExactly("anthropic", "claude-haiku-5-5", "LOW");
        assertThat(model(balanced)).containsExactly("anthropic", "claude-sonnet-5-5", "MEDIUM");
        assertThat(model(thorough)).containsExactly("anthropic", "claude-opus-5-5", "HIGH");
        assertThat(model(picked)).containsExactly("anthropic", "claude-opus-5-5", "LOW");
        assertThat(jdbc.sql("select definition->>'model' from agent_versions where agent_id = ?").param(thorough)
                .query(String.class).single()).isEqualTo("claude-opus-5-5");
    }

    private UUID agent(String setting) {
        UUID id = UUID.randomUUID();
        insert(id, "{\"name\":\"" + setting + "\",\"model\":\"" + setting + "\"}");
        return id;
    }

    private void insert(UUID id, String draft) {
        jdbc.sql("insert into agents (id, workspace_id, name, status, draft, revision, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', cast(? as jsonb), 1, now(), now())")
                .params(id, UUID.randomUUID(), id.toString().substring(0, 8), draft).update();
    }

    private List<String> model(UUID id) {
        Map<String, Object> row = jdbc.sql("select draft->>'provider' p, draft->>'model' m, draft->>'effort' e "
                + "from agents where id = ?").param(id).query().singleRow();
        return List.of((String) row.get("p"), (String) row.get("m"), (String) row.get("e"));
    }
}
