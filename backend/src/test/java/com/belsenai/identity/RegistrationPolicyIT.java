package com.belsenai.identity;

import com.belsenai.identity.entity.ExternalIdentity;
import com.belsenai.identity.entity.User;
import com.belsenai.identity.repository.ExternalIdentityRepository;
import com.belsenai.identity.repository.UserRepository;
import com.belsenai.identity.service.GoogleAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("local")
@TestPropertySource(properties = "belsen.registration.enabled=false")
class RegistrationPolicyIT extends PostgresHttpSupport {
    @Autowired UserRepository users;
    @Autowired ExternalIdentityRepository identities;
    @Autowired GoogleAccountService google;
    @Autowired PasswordEncoder passwords;

    @Test void disabledPolicyIsPublicMinimalAndUncached() throws Exception {
        try (var browser = browser()) {
            var response = browser.request("GET", "/api/v1/auth/policy", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).size()).isEqualTo(1);
            assertThat(mapper.readTree(response.body()).get("registrationEnabled").asBoolean()).isFalse();
            assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        }
    }
    @Test void disabledSignupWithValidCsrfCannotCreateAccountOrAuthenticate() throws Exception {
        String email = email();
        try (var browser = browser()) {
            browser.csrf();
            var response = browser.request("POST", "/api/v1/auth/register", registration(email), true);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("Self-registration is disabled").doesNotContain("password", "SQL", "Exception");
            for (int i = 0; i < 55; i++)
                assertThat(browser.request("POST", "/api/v1/auth/register", registration(email), true).statusCode()).isEqualTo(403);
            assertThat(users.findByEmail(email)).isEmpty();
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
        }
    }
    @Test void disabledSignupStillRequiresCsrf() throws Exception {
        try (var browser = browser()) {
            assertThat(browser.request("POST", "/api/v1/auth/register", registration(email()), false).statusCode()).isEqualTo(403);
        }
    }
    @Test void approvedExistingAccountRetainsRotationLoginAndLogout() throws Exception {
        String email = email();
        // Test fixture models offline individual provisioning; no special login or public override exists.
        users.saveAndFlush(new User(email, passwords.encode("approved individual password"), "Preview user", "UTC"));
        try (var browser = browser()) {
            browser.csrf();String anonymous = browser.cookie();
            var response = browser.request("POST", "/api/v1/auth/login", java.util.Map.of("email", email, "password", "approved individual password"), true);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(browser.cookie()).isNotEqualTo(anonymous);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(200);
            assertThat(browser.request("POST", "/api/v1/auth/logout", null, true).statusCode()).isEqualTo(403);
            browser.csrf();
            assertThat(browser.request("POST", "/api/v1/auth/logout", null, true).statusCode()).isEqualTo(204);
            assertThat(browser.request("GET", "/api/v1/me", null, false).statusCode()).isEqualTo(401);
        }
    }
    @Test void disabledRegistrationAlsoBlocksNewGoogleAccountsButPreservesExistingIdentity() {
        String subject = "approved-subject-" + java.util.UUID.randomUUID();
        var user = users.saveAndFlush(new User(email(), null, "Approved Google fixture", "UTC"));
        identities.saveAndFlush(new ExternalIdentity(user, "google", subject));
        assertThat(google.resolve(subject, user.getEmail(), true, "Existing")).isEqualTo(user.getId());
        String newEmail = email();
        assertThatThrownBy(() -> google.resolve("new-" + subject, newEmail, true, "Unapproved"))
                .isInstanceOf(com.belsenai.common.error.ApiException.class).hasMessageContaining("Self-registration is disabled");
        assertThat(users.findByEmail(newEmail)).isEmpty();
    }
}
