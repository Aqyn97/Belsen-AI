package com.belsenai.foundation;

import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PostgresFoundationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Autowired
    private SessionRepository<?> sessions;

    @Test
    void connectsAndAppliesAllMigrationsWithHibernateValidation() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo(POSTGRES.getDatabaseName());
        assertThat(flyway.info().applied()).hasSize(7);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void normalizedEmailMustBeUnique() {
        String email = UUID.randomUUID() + "@example.test";
        insertUser(UUID.randomUUID(), email);
        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), email))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonNormalizedEmailIsRejected() {
        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), "Mixed@Example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void googleOnlySchemaAllowsNoPasswordHash() {
        UUID id = UUID.randomUUID();
        insertUser(id, id + "@example.test");
        assertThat(jdbc.queryForObject("select count(*) from users where id = ? and password_hash is null",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void externalIdentityRequiresAnExistingOwner() {
        assertThatThrownBy(() -> insertIdentity(UUID.randomUUID(), UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providerSubjectCannotBelongToTwoUsers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertUser(first, first + "@example.test");
        insertUser(second, second + "@example.test");
        String subject = UUID.randomUUID().toString();
        insertIdentity(first, subject);
        assertThatThrownBy(() -> insertIdentity(second, subject))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionIsStoredReloadedAndDeletedInPostgres() {
        verifySessionRoundTrip(sessions);
    }

    private <S extends Session> void verifySessionRoundTrip(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setAttribute("foundation-check", "persisted");
        repository.save(session);
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?",
                Integer.class, session.getId())).isEqualTo(1);
        String primaryId = jdbc.queryForObject("select primary_id from spring_session where session_id = ?",
                String.class, session.getId());
        Session restored = repository.findById(session.getId());
        assertThat(restored).isNotNull();
        assertThat((String) restored.getAttribute("foundation-check")).isEqualTo("persisted");
        repository.deleteById(session.getId());
        assertThat(repository.findById(session.getId())).isNull();
        assertThat(jdbc.queryForObject("select count(*) from spring_session_attributes where session_primary_id = ?",
                Integer.class, primaryId)).isZero();
    }

    @Test
    void liveHealthEndpointExposesOnlyStatus() throws Exception {
        var response = send(HttpRequest.newBuilder(endpoint("/api/v1/health")).GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void livePrivateEndpointRequiresAuthentication() throws Exception {
        var response = send(HttpRequest.newBuilder(endpoint("/api/v1/me")).GET().build());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
    }

    @Test
    void liveMutationWithoutCsrfIsForbidden() throws Exception {
        var response = send(HttpRequest.newBuilder(endpoint("/api/v1/auth/register"))
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private URI endpoint(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void insertUser(UUID id, String email) {
        jdbc.update("""
                insert into users (id, email, display_name, timezone, created_at, updated_at)
                values (?, ?, 'Foundation test', 'Asia/Almaty', ?, ?)
                """, id, email, java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
    }

    private void insertIdentity(UUID userId, String subject) {
        jdbc.update("""
                insert into external_identities (id, user_id, provider, provider_subject, created_at)
                values (?, ?, 'google', ?, ?)
                """, UUID.randomUUID(), userId, subject, java.sql.Timestamp.from(Instant.now()));
    }
}
