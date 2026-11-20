package org.belsenfixture;

import com.belsenai.BelsenAiApplication;
import com.belsenai.coach.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Separate JVM replay proof. This class and its provider never enter the release JAR. */
public class CoachReplayProcess {
    @TestConfiguration(proxyBeanMethods=false)
    public static class Fixture {
        @Bean @Primary Clock replayClock() { return Clock.fixed(Instant.parse("2026-11-04T12:00:01Z"),ZoneOffset.UTC); }
        @Bean @Primary CoachProvider noDispatchProvider() { return evidence->{throw new AssertionError("Replay dispatched a provider request");}; }
    }
    public static void main(String[] args) {
        try(var context=new SpringApplicationBuilder(BelsenAiApplication.class,Fixture.class).profiles("local").run(
                "--server.port=0","--belsen.coach.enabled=true","--belsen.coach.api-key=test-only",
                "--belsen.coach.free-plan-confirmed=true","--belsen.coach.privacy-reviewed=true")) {
            var result=context.getBean(CoachAnalysis.class).analyse(UUID.fromString(args[0]),new CoachSelection(UUID.fromString(args[1]),null,null,1));
            if(!result.selectedIds().equals(List.of("c1"))) throw new AssertionError("Replay differs");
            System.out.println("COACH_REPLAY_OK");
        }
    }
}
