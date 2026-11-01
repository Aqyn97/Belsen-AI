package com.belsenai.identity;

import java.time.*;
import java.util.Locale;
import com.belsenai.identity.dto.RegisterRequest;
import com.belsenai.identity.security.GoogleConfig;
import com.belsenai.identity.security.LoginThrottle;
import com.belsenai.identity.service.EmailNormalizer;
import com.belsenai.identity.validation.PasswordValidator;
import com.belsenai.identity.validation.TimezoneValidator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import static org.assertj.core.api.Assertions.*;

class IdentityRulesTest {
    @Test void emailNormalizationIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(EmailNormalizer.normalize("  I@EXAMPLE.TEST  ")).isEqualTo("i@example.test");
        } finally { Locale.setDefault(original); }
    }

    @Test void passwordValidationChecksUtf8BytesAndDoesNotTrim() {
        var validator = new PasswordValidator();
        assertThat(validator.isValid("correct horse battery", null)).isTrue();
        assertThat(validator.isValid("short", null)).isFalse();
        assertThat(validator.isValid("é".repeat(37), null)).isFalse();
        assertThat(validator.isValid(" ".repeat(12), null)).isFalse();
    }

    @Test void registrationRejectsInvalidFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var errors = factory.getValidator().validate(new RegisterRequest("invalid", "short", "", "+05:00"));
            assertThat(errors).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test void namedTimezoneRequired() {
        var validator = new TimezoneValidator();
        assertThat(validator.isValid("Asia/Almaty", null)).isTrue();
        assertThat(validator.isValid("UTC", null)).isTrue();
        assertThat(validator.isValid("+05:00", null)).isFalse();
        assertThat(validator.isValid("Invented/Zone", null)).isFalse();
    }

    @Test void passwordsAreSaltedAndVerifiable() {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String first = encoder.encode("correct horse battery");
        assertThat(first).startsWith("{bcrypt}").isNotEqualTo(encoder.encode("correct horse battery"));
        assertThat(encoder.matches("correct horse battery", first)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
    }

    @Test void throttleCountsAttemptsAcrossAddresses() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < 10; i++) { throttle.check("account", "127.0.0.1"); }
        assertThatThrownBy(() -> throttle.check("account", "other-address"))
                .hasMessageContaining("Too many");
    }

    @Test void expiredThrottleWindowAllowsNewAttempts() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < 10; i++) { throttle.check("account", "127.0.0.1"); }
        now.set(now.get().plusSeconds(901));
        assertThatCode(() -> throttle.check("account", "127.0.0.1")).doesNotThrowAnyException();
    }

    @Test void disabledGoogleNeedsNoCredentials() {
        new ApplicationContextRunner().withUserConfiguration(GoogleConfig.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test void addressThrottleAppliesAcrossAccounts() {
        var throttle = new LoginThrottle();
        for (int i = 0; i < 50; i++) { throttle.check("account-" + i, "127.0.0.1"); }
        assertThatThrownBy(() -> throttle.check("next-account", "127.0.0.1"))
                .hasMessageContaining("Too many");
    }

    @Test void enabledGoogleRequiresBothCredentials() {
        new ApplicationContextRunner().withUserConfiguration(GoogleConfig.class)
                .withPropertyValues("belsen.google.enabled=true", "belsen.google.client-id=test-client")
                .run(context -> assertThat(context).hasFailed());
    }
}
