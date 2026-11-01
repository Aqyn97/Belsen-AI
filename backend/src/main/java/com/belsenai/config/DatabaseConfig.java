package com.belsenai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseConfig.Settings.class)
public class DatabaseConfig {

    /** Validates required configuration without creating or logging a connection. */
    @Validated
    @ConfigurationProperties("spring.datasource")
    public record Settings(
            @NotBlank(message = "Set SPRING_DATASOURCE_URL")
            @Pattern(regexp = "jdbc:postgresql:.*", message = "Use a PostgreSQL JDBC URL") String url,
            @NotBlank(message = "Set SPRING_DATASOURCE_USERNAME") String username,
            @NotBlank(message = "Set SPRING_DATASOURCE_PASSWORD") String password) {

        @Override
        public String toString() {
            return "Database settings [redacted]";
        }
    }
}
