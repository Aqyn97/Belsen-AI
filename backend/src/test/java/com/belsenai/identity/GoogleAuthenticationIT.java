package com.belsenai.identity;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.belsenai.identity.repository.UserRepository;
import com.belsenai.identity.service.GoogleAccountService;
import com.belsenai.identity.security.GoogleConfig;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("local")
@TestPropertySource(properties = {"belsen.google.enabled=true", "belsen.google.client-id=synthetic-client",
        "belsen.google.client-secret=synthetic-test-value"})
@Import(GoogleAuthenticationIT.MockProviderConfig.class)
class GoogleAuthenticationIT extends PostgresHttpSupport {
    static final MockProvider PROVIDER = new MockProvider();
    @Autowired GoogleAccountService accounts;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetProvider() { PROVIDER.invalidSignature = false; }

    @AfterAll static void stopProvider() { PROVIDER.server.stop(0); }

    @Test void newSubjectCreatesAccountAndRepeatedSubjectReusesIt() {
        String subject = UUID.randomUUID().toString();
        String email = email();
        UUID first = accounts.resolve(subject, email.toUpperCase(), true, "Google name");
        UUID repeated = accounts.resolve(subject, "changed-" + email, true, "Changed name");
        assertThat(repeated).isEqualTo(first);
        assertThat(users.findById(first).orElseThrow().getEmail()).isEqualTo(email);
        assertThat(users.findById(first).orElseThrow().getPasswordHash()).isNull();
        assertThat(users.findById(first).orElseThrow().getTimezone()).isEqualTo("UTC");
    }

    @Test void matchingEmailDoesNotMergeDifferentSubjectsOrPasswordAccounts() throws Exception {
        String email = email();
        accounts.resolve(UUID.randomUUID().toString(), email, true, "First");
        assertThatThrownBy(() -> accounts.resolve(UUID.randomUUID().toString(), email, true, "Second"))
                .hasMessageContaining("existing account");
        String passwordEmail = email();
        try (var browser = browser()) {
            browser.csrf();
            assertThat(browser.request("POST", "/api/v1/auth/register", registration(passwordEmail), true).statusCode()).isEqualTo(201);
        }
        long identities = jdbc.queryForObject("select count(*) from external_identities", Long.class);
        assertThatThrownBy(() -> accounts.resolve(UUID.randomUUID().toString(), passwordEmail, true, "Name"))
                .hasMessageContaining("existing account");
        assertThat(jdbc.queryForObject("select count(*) from external_identities", Long.class)).isEqualTo(identities);
    }

    @Test void unverifiedEmailCannotCreateAccount() {
        String email = email();
        assertThatThrownBy(() -> accounts.resolve(UUID.randomUUID().toString(), email, false, "Name"))
                .hasMessageContaining("verified email");
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test void mockSignedOidcCallbackCreatesPersistentSessionAndRotatesId() throws Exception {
        PROVIDER.subject = UUID.randomUUID().toString();
        PROVIDER.email = email();
        try (var browser = browser()) {
            browser.csrf();
            String before = browser.cookie();
            var start = browser.request("GET", "/oauth2/authorization/google", null, false);
            assertThat(start.statusCode()).isEqualTo(302);
            URI location = URI.create(start.headers().firstValue("Location").orElseThrow());
            Map<String, String> query = query(location.getRawQuery());
            assertThat(query.get("response_type")).isEqualTo("code");
            assertThat(query.get("redirect_uri")).endsWith("/login/oauth2/code/google");
            assertThat(query.get("nonce")).isNotBlank();
            PROVIDER.nonce = query.get("nonce");
            var callback = browser.request("GET", "/login/oauth2/code/google?code=mock-code&state="
                    + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8), null, false);
            assertThat(callback.statusCode()).withFailMessage(callback.body()).isEqualTo(302);
            assertThat(callback.headers().firstValue("Location")).contains("/auth/callback");
            assertThat(browser.cookie()).isNotEqualTo(before);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(200);
            UUID id = users.findByEmail(PROVIDER.email).orElseThrow().getId();
            assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
                    Integer.class, id.toString())).isEqualTo(1);
            assertThat(browser.request("PATCH", "/api/v1/me", Map.of("displayName", "Stale token"), true).statusCode()).isEqualTo(403);
            browser.csrf();
            assertThat(browser.request("PATCH", "/api/v1/me", Map.of("timezone", "Asia/Almaty"), true).statusCode()).isEqualTo(200);
        }
    }

    @Test void callbackWithWrongStateFailsBeforeTokenExchange() throws Exception {
        try (var browser = browser()) {
            browser.request("GET", "/oauth2/authorization/google", null, false);
            int before = PROVIDER.tokenCalls.get();
            var failure = browser.request("GET", "/login/oauth2/code/google?code=mock-code&state=invalid", null, false);
            assertThat(failure.statusCode()).isEqualTo(302);
            assertThat(failure.headers().firstValue("Location")).contains("/auth?error=google");
            assertThat(failure.body()).doesNotContain("mock-code", "invalid", "Exception");
            assertThat(PROVIDER.tokenCalls.get()).isEqualTo(before);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
        }
    }

    @Test void providerDenialHasSafeFailureResponse() throws Exception {
        try (var browser = browser()) {
            var start = browser.request("GET", "/oauth2/authorization/google", null, false);
            String state = query(URI.create(start.headers().firstValue("Location").orElseThrow()).getRawQuery()).get("state");
            var result = browser.request("GET", "/login/oauth2/code/google?error=access_denied&error_description=sensitive-provider-message&state="
                    + URLEncoder.encode(state, StandardCharsets.UTF_8), null, false);
            assertThat(result.statusCode()).isEqualTo(302);
            assertThat(result.headers().firstValue("Location")).contains("/auth?error=google");
            assertThat(result.body()).doesNotContain("sensitive-provider-message");
        }
    }

    @Test void invalidNonceDoesNotCreateAccountOrSession() throws Exception {
        PROVIDER.subject = UUID.randomUUID().toString(); PROVIDER.email = email();
        try (var browser = browser()) {
            var start = browser.request("GET", "/oauth2/authorization/google", null, false);
            var parameters = query(URI.create(start.headers().firstValue("Location").orElseThrow()).getRawQuery());
            PROVIDER.nonce = "wrong-nonce";
            var result = browser.request("GET", "/login/oauth2/code/google?code=mock-code&state="
                    + URLEncoder.encode(parameters.get("state"), StandardCharsets.UTF_8), null, false);
            assertThat(result.statusCode()).isEqualTo(302);
            assertThat(result.headers().firstValue("Location")).contains("/auth?error=google");
            assertThat(users.findByEmail(PROVIDER.email)).isEmpty();
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
        }
    }

    @Test void invalidSignatureIsRejected() throws Exception {
        PROVIDER.subject = UUID.randomUUID().toString(); PROVIDER.email = email();
        PROVIDER.invalidSignature = true;
        try (var browser = browser()) {
            var start = browser.request("GET", "/oauth2/authorization/google", null, false);
            var parameters = query(URI.create(start.headers().firstValue("Location").orElseThrow()).getRawQuery());
            PROVIDER.nonce = parameters.get("nonce");
            var result = browser.request("GET", "/login/oauth2/code/google?code=mock-code&state="
                    + URLEncoder.encode(parameters.get("state"), StandardCharsets.UTF_8), null, false);
            assertThat(result.statusCode()).isEqualTo(302);
            assertThat(result.headers().firstValue("Location")).contains("/auth?error=google");
            assertThat(users.findByEmail(PROVIDER.email)).isEmpty();
        }
    }

    @Test void emailCollisionDuringCallbackDoesNotAuthenticate() throws Exception {
        PROVIDER.subject = UUID.randomUUID().toString(); PROVIDER.email = email();
        try (var owner = browser(); var browser = browser()) {
            owner.csrf();
            owner.request("POST", "/api/v1/auth/register", registration(PROVIDER.email), true);
            var start = browser.request("GET", "/oauth2/authorization/google", null, false);
            var parameters = query(URI.create(start.headers().firstValue("Location").orElseThrow()).getRawQuery());
            PROVIDER.nonce = parameters.get("nonce");
            var result = browser.request("GET", "/login/oauth2/code/google?code=mock-code&state="
                    + URLEncoder.encode(parameters.get("state"), StandardCharsets.UTF_8), null, false);
            assertThat(result.statusCode()).isEqualTo(302);
            assertThat(result.headers().firstValue("Location")).contains("/auth?error=google");
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
        }
    }

    static Map<String, String> query(String query) {
        Map<String, String> values = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockProviderConfig {
        @Bean @Primary ClientRegistrationRepository mockClients() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                    .clientId("mock-client").clientSecret("synthetic-test-value")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri(PROVIDER.base + "/authorize").tokenUri(PROVIDER.base + "/token")
                    .userInfoUri(PROVIDER.base + "/userinfo").userNameAttributeName("sub")
                    .jwkSetUri(PROVIDER.base + "/jwks").issuerUri(PROVIDER.base).clientName("Mock Google").build());
        }
    }

    static class MockProvider {
        final HttpServer server;
        final String base;
        final RSAKey key;
        final AtomicInteger tokenCalls = new AtomicInteger();
        String subject;
        String email;
        String nonce;
        boolean invalidSignature;

        MockProvider() {
            try {
                key = new RSAKeyGenerator(2048).keyID("test-signing-key").generate();
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                base = "http://127.0.0.1:" + server.getAddress().getPort();
                server.createContext("/jwks", exchange -> reply(exchange, new JWKSet(key.toPublicJWK()).toString()));
                server.createContext("/userinfo", exchange -> reply(exchange,
                        "{\"sub\":\"" + subject + "\",\"email\":\"" + email + "\",\"email_verified\":true,\"name\":\"Mock user\"}"));
                server.createContext("/token", exchange -> {
                    tokenCalls.incrementAndGet();
                    try {
                        Instant now = Instant.now();
                        var claims = new JWTClaimsSet.Builder().issuer(base).subject(subject).audience("mock-client")
                                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                                .claim("nonce", nonce).claim("email", email).claim("email_verified", true).claim("name", "Mock user").build();
                        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
                        jwt.sign(new RSASSASigner(invalidSignature ? new RSAKeyGenerator(2048).generate() : key));
                        reply(exchange, "{\"access_token\":\"mock-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"openid profile email\",\"id_token\":\"" + jwt.serialize() + "\"}");
                    } catch (Exception exception) { throw new RuntimeException(exception); }
                });
                server.start();
            } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
        }

        void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
