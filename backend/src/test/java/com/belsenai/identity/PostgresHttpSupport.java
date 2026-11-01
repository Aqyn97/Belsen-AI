package com.belsenai.identity;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgresHttpSupport {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    Browser browser() { return new Browser(); }
    String email() { return UUID.randomUUID() + "@example.test"; }
    Map<String, Object> registration(String email) {
        return Map.of("email", email, "password", "correct horse battery", "displayName", "Test user", "timezone", "Asia/Almaty");
    }

    class Browser implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String token;
        String header;

        HttpResponse<String> csrf() throws Exception {
            var result = request("GET", "/api/v1/auth/csrf", null, false);
            JsonNode body = mapper.readTree(result.body());
            token = body.get("token").asString();
            header = body.get("headerName").asString();
            return result;
        }

        HttpResponse<String> request(String method, String path, Object body, boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (withCsrf) { request.header(header, token); }
            if (body == null) { request.method(method, HttpRequest.BodyPublishers.noBody()); }
            else { request.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(body instanceof String text ? text : mapper.writeValueAsString(body))); }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        String cookie() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("BELSEN_SESSION"))
                    .findFirst().orElseThrow().getValue();
        }

        @Override public void close() { client.close(); }
    }
}
