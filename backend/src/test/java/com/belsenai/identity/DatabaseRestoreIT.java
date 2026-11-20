package com.belsenai.identity;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** A real logical backup restored into a second disposable PostgreSQL database. */
@ActiveProfiles("local")
class DatabaseRestoreIT extends PostgresHttpSupport {
    @Test void restoresRowsMigrationsConstraintsAndImmutableHistory() throws Exception {
        try (var browser = browser()) {
            browser.csrf();
            assertThat(browser.request("POST", "/api/v1/auth/register", registration(email()), true).statusCode()).isEqualTo(201);
            browser.csrf();
            assertThat(browser.request("POST", "/api/v1/goals", Map.of("title", "Restore fixture", "target", 10, "unit", "pages"), true).statusCode()).isEqualTo(201);
            assertThat(browser.request("POST", "/api/v1/tasks", Map.of("title", "Restore history"), true).statusCode()).isEqualTo(201);
            String counts = "SELECT (SELECT count(*) FROM users), (SELECT count(*) FROM goals), "
                    + "(SELECT count(*) FROM task_events), (SELECT count(*) FROM spring_session), "
                    + "(SELECT count(*) FROM spring_session_attributes), "
                    + "(SELECT count(*) FROM flyway_schema_history WHERE success), "
                    + "(SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace), "
                    + "(SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal)";
            String expected = sql(POSTGRES.getDatabaseName(), counts).trim();
            assertThat(expected).startsWith("1|1|1|");
            run("pg_dump", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(), "-Fc", "-f", "/tmp/belsen-fixture.dump");
            run("createdb", "-U", POSTGRES.getUsername(), "belsen_restore");
            run("pg_restore", "-U", POSTGRES.getUsername(), "-d", "belsen_restore", "--no-owner", "--exit-on-error", "/tmp/belsen-fixture.dump");
            assertThat(sql("belsen_restore", counts).trim()).isEqualTo(expected);
            assertThat(sql("belsen_restore", "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success").trim()).isEqualTo("1,2,3,4,5,6,7");
            var immutable = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", "belsen_restore", "-v", "ON_ERROR_STOP=1", "-c", "UPDATE task_events SET sequence=sequence");
            assertThat(immutable.getExitCode()).isNotZero();
            assertThat(immutable.getStderr()).contains("Task events are append-only");
            assertThat(sql("belsen_restore", "SELECT count(*) FROM task_events").trim()).isEqualTo("1");
        }
    }
    private String sql(String database, String sql) throws Exception {
        return run("psql", "-U", POSTGRES.getUsername(), "-d", database, "-v", "ON_ERROR_STOP=1", "-At", "-c", sql);
    }
    private String run(String... command) throws Exception {
        var result = POSTGRES.execInContainer(command);
        assertThat(result.getExitCode()).withFailMessage("Disposable PostgreSQL command failed: %s", result.getStderr()).isZero();
        return result.getStdout();
    }
}
