package com.belsenai.identity;

import com.belsenai.identity.security.RegistrationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RegistrationPolicyTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(RegistrationPolicy.class);
    @Test void missingConfigurationFailsClosed() {
        context.run(app -> {
            var policy = app.getBean(RegistrationPolicy.class);
            assertThat(policy.enabled()).isFalse();
            assertThatThrownBy(policy::requireEnabled).hasMessageContaining("Self-registration is disabled");
        });
    }
    @Test void onlyExplicitTrueAllowsNewAccounts() {
        context.withPropertyValues("belsen.registration.enabled=true").run(app -> {
            var policy = app.getBean(RegistrationPolicy.class);
            assertThat(policy.enabled()).isTrue();
            assertThatCode(policy::requireEnabled).doesNotThrowAnyException();
        });
    }
}
