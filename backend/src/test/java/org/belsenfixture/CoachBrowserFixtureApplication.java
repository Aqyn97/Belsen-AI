package org.belsenfixture;

import com.belsenai.BelsenAiApplication;
import com.belsenai.coach.CoachProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;

/** Local browser fixture only: no HTTP provider, credentials or runtime test endpoint. */
public final class CoachBrowserFixtureApplication {
    private CoachBrowserFixtureApplication() { }
    public static void main(String[] args) {
        new SpringApplication(BelsenAiApplication.class, Fixture.class).run(args);
    }
    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean @Primary CoachProvider deterministicRanking() {
            return evidence -> {
                // Two candidates select the explicit timeout fixture; no external request is made.
                if (evidence.candidates().size() == 2) throw new com.belsenai.coach.CoachProviderFailure(504);
                return evidence.candidates().stream().limit(3).map(c -> c.id()).toList();
            };
        }
    }
}
