package com.belsenai.foundation;

import com.belsenai.config.RenderConfig;
import com.belsenai.config.ProductionConfig;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class RenderConfigTest {
    private MockEnvironment valid() {
        var env = new MockEnvironment()
                .withProperty("belsen.public-base-url", "https://fixture.onrender.com")
                .withProperty("RENDER_EXTERNAL_URL", "https://fixture.onrender.com")
                .withProperty("server.ssl.enabled", "false")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("server.tomcat.remoteip.internal-proxies", "10[.]1[.]2[.]3|10[.]1[.]2[.]4")
                .withProperty("spring.datasource.url", "jdbc:postgresql://ep-fixture.us-east-2.aws.neon.tech:5432/belsen?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=15&socketTimeout=30&tcpKeepAlive=true")
                .withProperty("spring.datasource.hikari.maximum-pool-size", "3")
                .withProperty("spring.datasource.hikari.minimum-idle", "0")
                .withProperty("spring.datasource.hikari.keepalive-time", "0");
        env.setActiveProfiles("production", "render");
        return env;
    }
    @Test void realProfilesBindPortTlsPoolAndCleanupWithoutConnecting() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ProductionConfig.class, RenderConfig.class)
                .withPropertyValues("spring.profiles.active=production,render", "PORT=17777",
                        "RENDER_EXTERNAL_URL=https://fixture.onrender.com",
                        "BELSEN_TRUSTED_PROXY_REGEX=10[.]1[.]2[.]3",
                        "SPRING_DATASOURCE_URL=" + valid().getProperty("spring.datasource.url"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var env = context.getEnvironment();
                    assertThat(env.getProperty("server.port")).isEqualTo("17777");
                    assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
                    assertThat(env.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
                    assertThat(env.getProperty("spring.session.jdbc.cleanup-cron")).isEqualTo("0 0 3 * * *");
                    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                    assertThat(env.getProperty("spring.datasource.hikari.idle-timeout")).isEqualTo("30000");
                    assertThat(env.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("30000");
                });
    }
    @Test void acceptsRestrictedFreeHostingContract() {
        assertThatCode(() -> new RenderConfig(valid())).doesNotThrowAnyException();
    }
    @Test void rejectsStandaloneProfileAndUnexpectedOrigins() {
        var env = valid(); env.setActiveProfiles("render");
        assertThatThrownBy(() -> new RenderConfig(env)).isInstanceOf(IllegalStateException.class);
        for (String origin : new String[]{"http://fixture.onrender.com", "https://attacker.invalid", "https://fixture.onrender.com/path"})
            assertThatThrownBy(() -> new RenderConfig(valid().withProperty("belsen.public-base-url", origin))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsBroadMalformedAndMissingProxyTrust() {
        for (String peers : new String[]{"", ".*", "10[.].*", "10.1.2.3", "10[.]1[.]2[.]999", "10[.]1[.]2[.]3|", "0[.]0[.]0[.]0"})
            assertThatThrownBy(() -> new RenderConfig(valid().withProperty("server.tomcat.remoteip.internal-proxies", peers))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsPooledDatabaseAndTlsDowngradesWithoutLoggingUrl() {
        String original = valid().getProperty("spring.datasource.url");
        for (String url : new String[]{original.replace("ep-fixture", "ep-fixture-pooler"), original.replace("verify-full", "require"), original + "&sslmode=disable", original.replace("DefaultJavaSSLFactory", "NonValidatingFactory"), original.replace("ep-fixture.us-east-2.aws.neon.tech", "localhost"), original + "&password=synthetic"})
            assertThatThrownBy(() -> new RenderConfig(valid().withProperty("spring.datasource.url", url)))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("synthetic").hasMessageNotContaining("jdbc:");
    }
    @Test void rejectsAccidentalPublicSignupProvidersAndPoolExpansion() {
        for (String flag : new String[]{"belsen.registration.enabled", "belsen.google.enabled", "belsen.coach.enabled"})
            assertThatThrownBy(() -> new RenderConfig(valid().withProperty(flag, "true"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RenderConfig(valid().withProperty("spring.datasource.hikari.maximum-pool-size", "10"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RenderConfig(valid().withProperty("spring.datasource.hikari.keepalive-time", "30000"))).isInstanceOf(IllegalStateException.class);
    }
}
