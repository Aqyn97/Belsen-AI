package com.belsenai.identity;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.belsenai.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ActiveProfiles("local")
class AuthenticationIT extends PostgresHttpSupport {
    @Autowired UserRepository users;
    @MockitoSpyBean PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test void registrationNormalizesEmailHashesPasswordAndPersistsSession() throws Exception {
        String email = email();
        try (var browser = browser()) {
            var csrf = browser.csrf();
            String cookie = csrf.headers().allValues("Set-Cookie").toString();
            assertThat(cookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
            String before = browser.cookie();
            var response = browser.request("POST", "/api/v1/auth/register", registration("  " + email.toUpperCase() + "  "), true);
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).doesNotContain("password", "hash", "bcrypt");
            assertThat(browser.cookie()).isNotEqualTo(before);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(200);
            var saved = users.findByEmail(email).orElseThrow();
            assertThat(encoder.matches("correct horse battery", saved.getPasswordHash())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
                    Integer.class, saved.getId().toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from spring_session_attributes where attribute_name = 'SPRING_SECURITY_CONTEXT'",
                    Integer.class)).isGreaterThan(0);
        }
    }

    @Test void duplicateEmailReturnsSafeConflict() throws Exception {
        String email = email();
        try (var first = browser(); var second = browser()) {
            first.csrf(); second.csrf();
            assertThat(first.request("POST", "/api/v1/auth/register", registration(email), true).statusCode()).isEqualTo(201);
            var duplicate = second.request("POST", "/api/v1/auth/register", registration(email.toUpperCase()), true);
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(duplicate.body()).doesNotContain("SQL", "password", "stackTrace");
        }
    }

    @Test void simultaneousRegistrationsHaveOneWinnerAndNoAuthenticatedLoser() throws Exception {
        String email = email();
        var hashingBarrier = new CyclicBarrier(2);
        // Keep real BCrypt. Both requests pass the email lookup before either can insert.
        doAnswer(invocation -> {
            Object hash = invocation.callRealMethod();
            hashingBarrier.await(10, TimeUnit.SECONDS);
            return hash;
        }).when(encoder).encode("correct horse battery");

        try (var first = browser(); var second = browser(); var executor = Executors.newFixedThreadPool(2)) {
            first.csrf(); second.csrf();
            var start = new CyclicBarrier(2);
            var firstFuture = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return first.request("POST", "/api/v1/auth/register", registration(email), true);
            });
            var secondFuture = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return second.request("POST", "/api/v1/auth/register",
                        registration("  " + email.toUpperCase(Locale.ROOT) + "  "), true);
            });
            var firstResponse = firstFuture.get(20, TimeUnit.SECONDS);
            var secondResponse = secondFuture.get(20, TimeUnit.SECONDS);
            assertThat(new int[] {firstResponse.statusCode(), secondResponse.statusCode()})
                    .containsExactlyInAnyOrder(201, 409);
            verify(encoder, times(2)).encode("correct horse battery");
            assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isEqualTo(1);

            boolean firstWon = firstResponse.statusCode() == 201;
            var winner = firstWon ? first : second;
            var loser = firstWon ? second : first;
            var conflict = firstWon ? secondResponse : firstResponse;
            assertThat(conflict.headers().firstValue("Content-Type").orElse(""))
                    .startsWith("application/problem+json");
            var problem = mapper.readTree(conflict.body());
            assertThat(problem.get("status").asInt()).isEqualTo(409);
            assertThat(problem.get("detail").asString()).isEqualTo("Account conflicts with an existing record.");
            assertThat(conflict.body()).doesNotContain(email, "correct horse battery", "password_hash",
                    "SQL", "constraint", "duplicate key", "Exception", "stackTrace");
            assertThat(winner.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(200);
            assertThat(loser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
            String loserSessionId = new String(java.util.Base64.getDecoder().decode(loser.cookie()),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(jdbc.queryForObject("""
                    select count(*) from spring_session_attributes a
                    join spring_session s on s.primary_id = a.session_primary_id
                    where s.session_id = ? and a.attribute_name = 'SPRING_SECURITY_CONTEXT'
                    """, Integer.class, loserSessionId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
                    Integer.class, users.findByEmail(email).orElseThrow().getId().toString())).isEqualTo(1);
        }
    }

    @Test void loginRotatesSessionAndOldCsrfCannotMutate() throws Exception {
        String email = email();
        try (var creator = browser(); var browser = browser()) {
            creator.csrf(); creator.request("POST", "/api/v1/auth/register", registration(email), true);
            browser.csrf(); String before = browser.cookie();
            var login = browser.request("POST", "/api/v1/auth/login", Map.of("email", email, "password", "correct horse battery"), true);
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(browser.cookie()).isNotEqualTo(before);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(200);
            assertThat(browser.request("PATCH", "/api/v1/me", Map.of("displayName", "Changed"), true).statusCode()).isEqualTo(403);
            browser.csrf();
            assertThat(browser.request("PATCH", "/api/v1/me", Map.of("displayName", "Changed"), true).statusCode()).isEqualTo(200);
        }
    }

    @Test void invalidCredentialsAreGenericForKnownAndUnknownEmail() throws Exception {
        String email = email();
        try (var browser = browser()) {
            browser.csrf(); browser.request("POST", "/api/v1/auth/register", registration(email), true);
        }
        try (var browser = browser()) {
            browser.csrf();
            for (String address : new String[] {email, email()}) {
                var response = browser.request("POST", "/api/v1/auth/login", Map.of("email", address, "password", "wrong password"), true);
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(mapper.readTree(response.body()).get("detail").asString()).isEqualTo("Invalid email or password.");
            }
        }
    }

    @Test void logoutInvalidatesCookieAndStoredSession() throws Exception {
        try (var browser = browser()) {
            browser.csrf(); browser.request("POST", "/api/v1/auth/register", registration(email()), true);
            String old = browser.cookie();
            browser.csrf();
            var logout = browser.request("POST", "/api/v1/auth/logout", null, true);
            assertThat(logout.statusCode()).isEqualTo(204);
            assertThat(logout.headers().allValues("Set-Cookie").toString()).contains("Max-Age=0");
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
            try (var client = HttpClient.newHttpClient()) {
                var replay = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/me"))
                        .header("Cookie", "BELSEN_SESSION=" + old).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(replay.statusCode()).isEqualTo(401);
            }
            String raw = new String(java.util.Base64.getDecoder().decode(old), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class, raw)).isZero();
        }
    }

    @Test void profileUpdateIsOwnedAndTimezoneValidated() throws Exception {
        String first = email(); String second = email();
        try (var a = browser(); var b = browser()) {
            a.csrf(); b.csrf();
            a.request("POST", "/api/v1/auth/register", registration(first), true);
            b.request("POST", "/api/v1/auth/register", registration(second), true);
            a.csrf();
            assertThat(a.request("PATCH", "/api/v1/me", Map.of("displayName", "Updated", "timezone", "Europe/Paris"), true).statusCode()).isEqualTo(200);
            assertThat(users.findByEmail(second).orElseThrow().getDisplayName()).isEqualTo("Test user");
            assertThat(a.request("PATCH", "/api/v1/me", Map.of("timezone", "+05:00"), true).statusCode()).isEqualTo(400);
            assertThat(a.request("PATCH", "/api/v1/me", Map.of("userId", users.findByEmail(second).orElseThrow().getId()), true).statusCode()).isEqualTo(400);
            assertThat(a.request("PATCH", "/api/v1/me", Map.of("accountId", "injected"), true).statusCode()).isEqualTo(400);
            assertThat(a.request("PATCH", "/api/v1/me", Map.of(), true).statusCode()).isEqualTo(400);
        }
    }

    @Test void invalidRegistrationAndMalformedJsonAreSafe() throws Exception {
        try (var browser = browser()) {
            browser.csrf();
            var result = browser.request("POST", "/api/v1/auth/register", Map.of("email", "bad", "password", "short", "displayName", "Name", "timezone", "UTC"), true);
            assertThat(result.statusCode()).isEqualTo(400);
            assertThat(mapper.readTree(result.body()).get("fieldErrors").has("password")).isTrue();
            assertThat(result.body()).doesNotContain("short");
            assertThat(browser.request("POST", "/api/v1/auth/register", "{", true).statusCode()).isEqualTo(400);
            assertThat(browser.request("POST", "/api/v1/auth/register", Map.of("email", email(), "password", "é".repeat(37), "displayName", "Name", "timezone", "UTC"), true).statusCode()).isEqualTo(400);
        }
    }

    @Test void anonymousAndCsrfFailuresAreProblems() throws Exception {
        try (var browser = browser()) {
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
            var result = browser.request("POST", "/api/v1/auth/register", registration(email()), false);
            assertThat(result.statusCode()).isEqualTo(403);
            assertThat(mapper.readTree(result.body()).has("requestId")).isTrue();
        }
    }

    @Test void disabledGoogleRouteDoesNotStartOAuth() throws Exception {
        try (var browser = browser()) {
            assertThat(browser.request("GET", "/oauth2/authorization/google", null, false).statusCode()).isEqualTo(401);
        }
    }

    @Test void loginAttemptsAreThrottled() throws Exception {
        try (var browser = browser()) {
            browser.csrf(); String email = email();
            for (int i = 0; i < 10; i++) {
                assertThat(browser.request("POST", "/api/v1/auth/login", Map.of("email", email, "password", "wrong password"), true).statusCode()).isEqualTo(401);
            }
            var blocked = browser.request("POST", "/api/v1/auth/login", Map.of("email", email, "password", "wrong password"), true);
            assertThat(blocked.statusCode()).isEqualTo(429);
            assertThat(blocked.headers().firstValue("Retry-After")).contains("900");
        }
    }
}
