package com.belsenai.identity;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@TestPropertySource(properties = {"belsen.google.enabled=true", "belsen.google.client-id=synthetic-client",
        "belsen.google.client-secret=synthetic-test-value"})
class GoogleEnabledConfigurationIT extends PostgresHttpSupport {
    @Test void enabledGoogleStartsAndRedirectsWithoutContactingProvider() throws Exception {
        try (var browser = browser()) {
            var response = browser.request("GET", "/oauth2/authorization/google", null, false);
            assertThat(response.statusCode()).isEqualTo(302);
            URI redirect = URI.create(response.headers().firstValue("Location").orElseThrow());
            assertThat(redirect.getHost()).isEqualTo("accounts.google.com");
            assertThat(redirect.getRawQuery()).contains("scope=openid", "state=", "nonce=");
        }
    }
}
