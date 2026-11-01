package com.belsenai.identity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductionCookieIT extends PostgresHttpSupport {
    @Test void productionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
        try (var browser = browser()) {
            String cookie = browser.csrf().headers().allValues("Set-Cookie").toString();
            assertThat(cookie).contains("BELSEN_SESSION=", "Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        }
    }
}
