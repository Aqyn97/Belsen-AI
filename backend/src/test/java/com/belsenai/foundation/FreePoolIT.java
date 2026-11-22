package com.belsenai.foundation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class FreePoolIT {
    @Test void boundedPoolSupportsFlywayFreshMigrationsAndRestartValidation() {
        try (var pg = new PostgreSQLContainer<>("postgres:17.11-bookworm")) {
            pg.start();
            var config = new HikariConfig();
            config.setJdbcUrl(pg.getJdbcUrl()); config.setUsername(pg.getUsername()); config.setPassword(pg.getPassword());
            config.setMaximumPoolSize(3); config.setMinimumIdle(0);
            config.setIdleTimeout(30_000); config.setMaxLifetime(300_000); config.setKeepaliveTime(0);
            config.setConnectionTimeout(30_000);
            try (var pool = new HikariDataSource(config)) {
                var flyway = Flyway.configure().dataSource(pool).cleanDisabled(true).load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
                flyway.validate();
                // Same fresh datasource behavior as a restart against an existing schema.
            }
            try (var pool = new HikariDataSource(config)) {
                var flyway = Flyway.configure().dataSource(pool).cleanDisabled(true).load();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                flyway.validate();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isLessThanOrEqualTo(3);
            }
        }
    }
}
