package com.belsenai.identity;

import com.belsenai.coach.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("local")
@TestPropertySource(properties={"belsen.coach.enabled=true","belsen.coach.api-key=test-only","belsen.coach.free-plan-confirmed=true","belsen.coach.privacy-reviewed=true"})
class CoachRankingIT extends PostgresHttpSupport {
    @MockitoBean CoachProvider provider;
    @MockitoBean Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired CoachAnalysis analysis;
    @Autowired CoachRequests requests;
    @Autowired CoachResults results;
    volatile Instant now;
    @BeforeEach void setup() {
        now=Instant.parse("2026-11-04T12:00:00Z");when(clock.instant()).thenAnswer(i->now);
        jdbc.update("delete from coach_results");jdbc.update("delete from coach_requests");jdbc.update("delete from coach_daily_usage");
        when(provider.rank(any())).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return List.of("c1");});
    }
    record Account(Browser b,UUID id) implements AutoCloseable { public void close(){b.close();} }
    Account account(boolean goal) throws Exception {
        var b=browser();b.csrf();var r=b.request("POST","/api/v1/auth/register",registration(email()),true);assertThat(r.statusCode()).isEqualTo(201);b.csrf();
        UUID id=UUID.fromString(mapper.readTree(r.body()).get("id").asString());
        if(goal) jdbc.update("insert into goals(id,owner_id,title,target,unit,created_at,updated_at,version) values(?,?,'PRIVATE INSTRUCTION',10,'SECRET_UNIT',?,?,0)",UUID.randomUUID(),id,java.sql.Timestamp.from(now.minusSeconds(1)),java.sql.Timestamp.from(now.minusSeconds(1)));
        return new Account(b,id);
    }
    CoachSelection selection() {return new CoachSelection(UUID.randomUUID(),null,null,1);}
    @Test void completedReplayKeepsOriginalEvidenceAfterDataChangesAndIsOwnerScoped() throws Exception {
        try(var a=account(true);var b=account(true)) {
            var input=selection();var first=analysis.analyse(a.id,input);
            jdbc.update("update goals set title='CHANGED',target=99 where owner_id=?",a.id);
            now=now.plusSeconds(1);assertThat(analysis.analyse(a.id,input)).isEqualTo(first);
            assertThat(results.find(b.id,input.submissionId())).isNull();
            assertThat(first.recommendations()).hasSize(1);assertThat(first.source()).isEqualTo("AI_PRIORITIZED");
            assertThat(jdbc.queryForObject("select state from coach_requests where owner_id=?",String.class,a.id)).isEqualTo("COMPLETED");
            String stored=jdbc.queryForObject("select result_json from coach_results where owner_id=?",String.class,a.id);
            assertThat(stored).doesNotContain("PRIVATE INSTRUCTION","SECRET_UNIT","CHANGED","choices","Authorization","test-only");
            verify(provider,times(1)).rank(any());
        }
    }
    @Test void emptyAccountAndReadEndpointsHaveZeroProviderCalls() throws Exception {
        try(var a=account(false)) {
            assertThat(a.b.request("GET","/api/v1/coach/status",null,false).statusCode()).isEqualTo(200);
            var input=selection();var result=analysis.analyse(a.id,input);assertThat(result.source()).isEqualTo("RULES_ONLY");
            assertThat(analysis.analyse(a.id,input)).isEqualTo(result);verify(provider,never()).rank(any());
            assertThat(jdbc.queryForObject("select count(*) from coach_daily_usage",Integer.class)).isZero();
        }
    }
    @Test void simultaneousDuplicateAndPrivateMutationsDispatchOnlyOnce() throws Exception {
        try(var a=account(true);var pool=Executors.newSingleThreadExecutor()) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            when(provider.rank(any())).thenAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return List.of("c1");});
            var input=selection();var job=pool.submit(()->analysis.analyse(a.id,input));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->analysis.analyse(a.id,input)).isInstanceOf(com.belsenai.common.error.ApiException.class);
                assertThat(a.b.request("POST","/api/v1/coach/analyses",selection(),false).statusCode()).isEqualTo(403);
            } finally{release.countDown();}
            assertThat(job.get(10,TimeUnit.SECONDS).selectedIds()).containsExactly("c1");verify(provider,times(1)).rank(any());
        }
    }
    @Test void failuresAreUncertainAndCannotRedispatchOrReleaseOwnerCapacity() throws Exception {
        try(var a=account(true)) {
            when(provider.rank(any())).thenThrow(new CoachProviderFailure(429));var input=selection();
            assertThatThrownBy(()->analysis.analyse(a.id,input)).isInstanceOf(CoachProviderFailure.class);
            assertThat(requests.inspect(a.id,input.submissionId()).state()).isEqualTo(CoachRequests.State.OUTCOME_UNKNOWN);
            assertThatThrownBy(()->analysis.analyse(a.id,input)).isInstanceOf(com.belsenai.common.error.ApiException.class);
            assertThatThrownBy(()->analysis.analyse(a.id,selection())).isInstanceOf(CoachLimitException.class);
            verify(provider,times(1)).rank(any());assertThat(jdbc.queryForObject("select count(*) from coach_results",Integer.class)).isZero();
        }
    }
    @Test void accountQuotaCountsFreeAttemptsAndNeverChargesMonetaryReservation() throws Exception {
        try(var a=account(true)) {
            for(int i=0;i<3;i++) analysis.analyse(a.id,selection());
            assertThatThrownBy(()->analysis.analyse(a.id,selection())).isInstanceOf(CoachLimitException.class);
            verify(provider,times(3)).rank(any());
            assertThat(jdbc.queryForObject("select sum(reserved_usd) from coach_daily_usage",java.math.BigDecimal.class)).isEqualByComparingTo("0");
        }
    }
    @Test void replayExpiresAtTwentyFourHoursAndCleanupKeepsTombstone() throws Exception {
        try(var a=account(true)) {
            var input=selection();analysis.analyse(a.id,input);now=now.plus(Duration.ofHours(24));
            assertThatThrownBy(()->analysis.analyse(a.id,input)).isInstanceOf(com.belsenai.common.error.ApiException.class).hasMessageContaining("expired");
            requests.cleanup();assertThat(results.find(a.id,input.submissionId())).isNull();
            assertThat(requests.inspect(a.id,input.submissionId()).state()).isEqualTo(CoachRequests.State.COMPLETED);
            verify(provider,times(1)).rank(any());
        }
    }
    @Test void invalidRankingsAreNeverPersistedAsSuccessfulInference() throws Exception {
        try(var a=account(true)) {
            when(provider.rank(any())).thenReturn(List.of("foreign-id"));
            assertThatThrownBy(()->analysis.analyse(a.id,selection())).isInstanceOf(CoachProviderFailure.class);
            assertThat(jdbc.queryForObject("select count(*) from coach_results",Integer.class)).isZero();
        }
    }
    @Test void replaySurvivesAnActualJvmRestartWithoutProviderDispatch() throws Exception {
        try(var a=account(true)) {
            var input=selection();analysis.analyse(a.id,input);
            var log=java.nio.file.Files.createTempFile("belsen-replay-",".log");
            Process child=null;
            try {
                String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
                var builder=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",cp,"org.belsenfixture.CoachReplayProcess",a.id.toString(),input.submissionId().toString());
                builder.environment().put("SPRING_DATASOURCE_URL",POSTGRES.getJdbcUrl());
                builder.environment().put("SPRING_DATASOURCE_USERNAME",POSTGRES.getUsername());
                builder.environment().put("SPRING_DATASOURCE_PASSWORD",POSTGRES.getPassword());
                child=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
                assertThat(child.waitFor(45,TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).withFailMessage(java.nio.file.Files.readString(log)).isZero();
                assertThat(java.nio.file.Files.readString(log)).contains("COACH_REPLAY_OK");
            } finally {if(child!=null && child.isAlive()) child.destroyForcibly();java.nio.file.Files.deleteIfExists(log);}
            verify(provider,times(1)).rank(any());
        }
    }
    @Test void failedResultInsertRollsBackCompletionAndV7GuardsOwnershipAndImmutability() throws Exception {
        try(var a=account(true);var b=account(false)) {
            var input=selection();requests.claim(a.id,input.submissionId(),input.fingerprint());requests.reserveFree(a.id,input.submissionId());requests.processing(a.id,input.submissionId());
            assertThatThrownBy(()->requests.finish(a.id,input.submissionId(),"invalid-json",now,now.plus(Duration.ofHours(24)),false)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(requests.inspect(a.id,input.submissionId()).state()).isEqualTo(CoachRequests.State.PROCESSING);
            assertThat(jdbc.queryForObject("select count(*) from coach_results",Integer.class)).isZero();
            // A separate successful request gives a valid row to test SQL ownership and immutable replay.
            requests.uncertain(a.id,input.submissionId());
            try(var c=account(true)) {
                var completed=selection();analysis.analyse(c.id,completed);
                assertThatThrownBy(()->jdbc.update("update coach_results set result_json='{}' where owner_id=?",c.id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThatThrownBy(()->jdbc.update("insert into coach_results(owner_id,submission_id,result_json,created_at,expires_at) values(?,?,'{}',?,?)",b.id,completed.submissionId(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.plus(Duration.ofHours(24))))).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThat(jdbc.queryForObject("select count(*) from pg_indexes where tablename='coach_results' and indexname='coach_results_expiry_idx'",Integer.class)).isEqualTo(1);
            }
        }
    }

    @Test void authenticatedHttpContractReplaysAndReturnsSafeProviderErrors() throws Exception {
        try(var a=account(true)) {
            var input=selection();
            var first=a.b.request("POST","/api/v1/coach/analyses",input,true);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(mapper.readTree(first.body()).get("selectedIds").get(0).asString()).isEqualTo("c1");
            assertThat(a.b.request("POST","/api/v1/coach/analyses",input,true).body()).isEqualTo(first.body());
            assertThat(first.body()).doesNotContain("PRIVATE INSTRUCTION","SECRET_UNIT",a.id.toString(),"test-only");
            when(provider.rank(any())).thenThrow(new CoachProviderFailure(429));
            var failed=a.b.request("POST","/api/v1/coach/analyses",selection(),true);
            assertThat(failed.statusCode()).isEqualTo(429);assertThat(failed.headers().firstValue("Retry-After")).contains("60");
            assertThat(failed.body()).contains("requestId").doesNotContain("test-only","api.groq.com","Authorization");
            verify(provider,times(2)).rank(any());
        }
    }

}
