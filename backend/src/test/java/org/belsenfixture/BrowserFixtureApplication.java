package org.belsenfixture;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.belsenai.BelsenAiApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Browser-only launcher, outside production scanning and absent from the application JAR. */
public final class BrowserFixtureApplication {
    private BrowserFixtureApplication() { }
    public static void main(String[] args) {
        new SpringApplication(BelsenAiApplication.class, BrowserClock.class).run(args);
    }
    @Configuration(proxyBeanMethods = false)
    static class BrowserClock {
        @Bean @Primary Clock browserReportingClock() {
            // A deterministic Monday with advancing acceptance instants; no test endpoint or production option.
            Clock system = Clock.systemUTC();
            return Clock.offset(system, Duration.between(system.instant(), Instant.parse("2026-11-02T12:00:00Z")));
        }
    }
}
