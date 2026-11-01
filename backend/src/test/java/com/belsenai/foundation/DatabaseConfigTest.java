package com.belsenai.foundation;

import com.belsenai.config.DatabaseConfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DatabaseConfig.class);

    @Test
    void missingDatabaseSettingsFailValidation() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validPostgresSettingsBindWithoutOpeningConnection() {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5433/belsen",
                "spring.datasource.username=test-user", "spring.datasource.password=test-only")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DatabaseConfig.Settings.class).toString())
                            .isEqualTo("Database settings [redacted]");
                });
    }

    @Test
    void nonPostgresUrlIsRejected() {
        runner.withPropertyValues("spring.datasource.url=jdbc:other://localhost/database",
                "spring.datasource.username=test-user", "spring.datasource.password=test-only")
                .run(context -> assertThat(context).hasFailed());
    }
}
