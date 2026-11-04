package com.belsenai.identity;

import java.time.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.persistence.EntityManager;
import com.belsenai.habits.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("local")
class HabitsIT extends PostgresHttpSupport {
    @MockitoBean Clock clock;
    @MockitoSpyBean HabitCheckinEventRepository events;
    @MockitoSpyBean HabitScheduleVersionRepository schedules;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    final AtomicReference<Instant> now=new AtomicReference<>();
    @BeforeEach void time() { now.set(Instant.parse("2026-11-02T12:00:00Z"));when(clock.instant()).thenAnswer(call -> now.get()); }
    Browser signedIn() throws Exception {
        Browser b=browser();b.csrf();assertThat(b.request("POST","/api/v1/auth/register",registration(email()),true).statusCode()).isEqualTo(201);b.csrf();return b;
    }
    Map<String,Object> input() { return Map.of("title","Read","weekdays",List.of(1,2,3,4,5,6,7),"startDate","2026-11-02"); }
    JsonNode created(Browser b) throws Exception {
        var response=b.request("POST","/api/v1/habits",input(),true);assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);return mapper.readTree(response.body());
    }
    String path(JsonNode h) { return "/api/v1/habits/"+h.get("id").asString(); }
    JsonNode get(Browser b,String p) throws Exception { var r=b.request("GET",p,null,false);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return mapper.readTree(r.body()); }
    long version(Browser b,String p) throws Exception { return get(b,p).get("version").asLong(); }
    Map<String,Object> action(long version) { return Map.of("version",version,"weekStart","2026-11-02"); }
    Map<String,Object> rest(long version) { return Map.of("version",version,"weekStart","2026-11-02","reason","Travel"); }
    Map<String,Object> schedule(long version,String effective,List<Integer> days) {
        return Map.of("version",version,"effectiveFrom",effective,"weekdays",days,"startDate","2026-11-02");
    }
    HttpResponse<String> delete(Browser b,String p,String match,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+p)).DELETE();
        if(match!=null)request.header("If-Match",match);if(csrf)request.header(b.header,b.token);
        return b.client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode history(Browser b,String p) throws Exception { return get(b,p+"/history?from=2026-11-02&to=2026-11-08"); }
    JsonNode firstDay(Browser b,String p) throws Exception { return history(b,p).get("days").get("items").get(0); }
    @Test void createListGetAndMetadataPatchPreserveOwnershipAndNullableFields() throws Exception {
        try(var b=signedIn()) {
            var h=created(b);String p=path(h);
            assertThat(h.get("reportingWeek").get("timezone").asString()).isEqualTo("Asia/Almaty");
            assertThat(get(b,"/api/v1/habits").get("totalElements").asLong()).isEqualTo(1);
            assertThat(b.request("PATCH",p,Map.of("version",0,"description","Library"),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PATCH",p,Map.of("version",1,"title","Study"),true).statusCode()).isEqualTo(200);
            assertThat(get(b,p).get("description").asString()).isEqualTo("Library");
            assertThat(b.request("PATCH",p,"{\"version\":2,\"description\":null}",true).statusCode()).isEqualTo(200);
            assertThat(get(b,p).get("description").isNull()).isTrue();
        }
    }
    @Test void validationRejectsEmptyDuplicateAndOutOfRangeSchedulesAndPastCreation() throws Exception {
        try(var b=signedIn()) {
            for(var days:List.of(List.of(),List.of(0),List.of(8),List.of(1,1))) {
                var body=new HashMap<>(input());body.put("weekdays",days);assertThat(b.request("POST","/api/v1/habits",body,true).statusCode()).isEqualTo(400);
            }
            for(var pair:List.of(Map.entry("title"," "),Map.entry("startDate","2026-11-01"),Map.entry("endDate","2026-11-01"),Map.entry("ownerId",UUID.randomUUID().toString()))) {
                var body=new HashMap<>(input());body.put(pair.getKey(),pair.getValue());assertThat(b.request("POST","/api/v1/habits",body,true).statusCode()).isEqualTo(400);
            }
            var h=created(b);String p=path(h);
            assertThat(b.request("PATCH",p,Map.of("version",0),true).statusCode()).isEqualTo(400);
            assertThat(b.request("PATCH",p,"{\"version\":0,\"title\":null}",true).statusCode()).isEqualTo(400);
            assertThat(b.request("POST",p+"/schedules",schedule(0,"2026-11-02",List.of(2)),true).statusCode()).isEqualTo(409);
            assertThat(b.request("GET",p+"/history",null,false).statusCode()).isEqualTo(400);
            assertThat(b.request("GET",p+"/history?from=2026-01-01&to=2026-11-08",null,false).statusCode()).isEqualTo(400);
            assertThat(b.request("GET","/api/v1/habits?size=101",null,false).statusCode()).isEqualTo(400);
        }
    }
    @Test void completeUndoAndMatchingVersionRetriesPreserveEvents() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(200);
            assertThat(firstDay(b,p).get("completed").asBoolean()).isTrue();
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(1),true).statusCode()).isEqualTo(200);
            assertThat(delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"1\"",true).statusCode()).isEqualTo(200);
            assertThat(delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"2\"",true).statusCode()).isEqualTo(200);
            assertThat(firstDay(b,p).get("completed").asBoolean()).isFalse();
            var h=history(b,p);assertThat(h.get("actions").get("totalElements").asLong()).isEqualTo(2);
            assertThat(h.get("actions").get("items").get(1).get("sequence").asLong()).isEqualTo(2);
            assertThat(version(b,p)).isEqualTo(2);
        }
    }
    @Test void deleteRequiresStrictStrongIfMatchAndWeekContext() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b))+"/completions/2026-11-02?weekStart=2026-11-02";
            for(String bad:List.of("0","*","W/\"0\"","\"01\"","\"9223372036854775808\"","\"0\",\"1\"")) assertThat(delete(b,p,bad,true).statusCode()).isEqualTo(400);
            assertThat(delete(b,p,null,true).statusCode()).isEqualTo(400);
            assertThat(delete(b,p.split("\\?")[0],"\"0\"",true).statusCode()).isEqualTo(400);
        }
    }
    @Test void restPlanningCancellationAndNoopsRetainReasons() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));String date="2026-11-03";
            assertThat(b.request("PUT",p+"/rest-days/"+date,rest(0),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/rest-days/"+date,rest(1),true).statusCode()).isEqualTo(200);
            var row=history(b,p).get("days").get("items").get(1);
            assertThat(row.get("restReason").asString()).isEqualTo("Travel");assertThat(row.get("restApplicable").asBoolean()).isTrue();
            assertThat(delete(b,p+"/rest-days/"+date+"?weekStart=2026-11-02","\"0\"",true).statusCode()).isEqualTo(409);
            assertThat(delete(b,p+"/rest-days/"+date+"?weekStart=2026-11-02","\"1\"",true).statusCode()).isEqualTo(200);
            assertThat(delete(b,p+"/rest-days/"+date+"?weekStart=2026-11-02","\"2\"",true).statusCode()).isEqualTo(200);
            assertThat(history(b,p).get("actions").get("totalElements").asLong()).isEqualTo(2);
        }
    }
    @Test void noRetroactiveRestOrCancellationAndNoCompletionOnRest() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            assertThat(b.request("PUT",p+"/rest-days/2026-11-02",rest(0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true).statusCode()).isEqualTo(200);
            now.set(Instant.parse("2026-11-02T19:00:00Z"));
            assertThat(b.request("PUT",p+"/completions/2026-11-03",action(1),true).statusCode()).isEqualTo(409);
            assertThat(delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"1\"",true).statusCode()).isEqualTo(409);
        }
    }
    @Test void futureUnscheduledWrongWeekAndClosedCheckinsAreRejected() throws Exception {
        try(var b=signedIn()) {
            var body=new HashMap<>(input());body.put("weekdays",List.of(1));
            var h=mapper.readTree(b.request("POST","/api/v1/habits",body,true).body());String p=path(h);
            assertThat(b.request("PUT",p+"/completions/2026-11-03",action(0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",Map.of("version",0,"weekStart","2026-10-26"),true).statusCode()).isEqualTo(400);
            now.set(Instant.parse("2026-11-08T19:00:00Z"));
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(409);
            assertThat(delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"0\"",true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void futureRevisionDoesNotEraseTodayAndLatestAcceptedRevisionSupersedesOlderPending() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            assertThat(b.request("POST",p+"/schedules",schedule(0,"2026-11-05",List.of(4)),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/schedules",schedule(1,"2026-11-03",List.of(2)),true).statusCode()).isEqualTo(200);
            var days=history(b,p).get("days").get("items");
            assertThat(days.get(0).get("scheduled").asBoolean()).isTrue();assertThat(days.get(1).get("scheduled").asBoolean()).isTrue();
            assertThat(days.get(3).get("scheduled").asBoolean()).isFalse();
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(2),true).statusCode()).isEqualTo(200);
        }
    }
    @Test void nowInapplicableRestRemainsAuditableAndCanBeCancelledBeforeStart() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true);
            b.request("POST",p+"/schedules",schedule(1,"2026-11-03",List.of(3)),true);
            var row=history(b,p).get("days").get("items").get(1);
            assertThat(row.get("restPlanned").asBoolean()).isTrue();assertThat(row.get("restApplicable").asBoolean()).isFalse();
            assertThat(delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"2\"",true).statusCode()).isEqualTo(200);
            assertThat(history(b,p).get("actions").get("totalElements").asLong()).isEqualTo(2);
        }
    }
    @Test void archivePreservesTodayAndCannotBeUndoneByAnOlderPendingSchedule() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            b.request("POST",p+"/schedules",schedule(0,"2026-11-05",List.of(4)),true);
            assertThat(b.request("POST",p+"/archive",Map.of("version",1),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/archive",Map.of("version",2),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(2),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PATCH",p,Map.of("version",3,"title","Cannot"),true).statusCode()).isEqualTo(409);
            assertThat(history(b,p).get("days").get("items").get(3).get("scheduled").asBoolean()).isFalse();
            assertThat(get(b,"/api/v1/habits").get("totalElements").asLong()).isZero();
            assertThat(get(b,"/api/v1/habits?archived=true").get("totalElements").asLong()).isEqualTo(1);
        }
    }
    @Test void asOfHistoryFiltersSchedulesEventsAndArchiveAndNeverWritesMissingDates() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));
            b.request("PUT",p+"/completions/2026-11-02",action(0),true);Instant beforeUndo=now.get().plusSeconds(1);now.set(beforeUndo);
            String query=p+"/history?from=2026-11-02&to=2026-11-08&cutoff="+beforeUndo;
            var before=get(b,query);assertThat(before.get("days").get("items").get(0).get("completed").asBoolean()).isTrue();
            now.set(now.get().plusSeconds(1));delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"1\"",true);
            b.request("POST",p+"/schedules",schedule(2,"2026-11-03",List.of(3)),true);
            b.request("POST",p+"/archive",Map.of("version",3),true);
            var after=get(b,query);
            assertThat(after.get("days").get("items").get(0).get("completed").asBoolean()).isTrue();
            assertThat(after.get("days").get("items").get(1).get("scheduled").asBoolean()).isTrue();
            assertThat(after.get("actions").get("totalElements").asLong()).isEqualTo(1);
            assertThat(after.get("schedules").get("totalElements").asLong()).isEqualTo(1);
            assertThat(firstDay(b,p).get("completed").asBoolean()).isFalse();
            UUID id=UUID.fromString(p.substring(p.lastIndexOf('/')+1));
            assertThat(jdbc.queryForObject("select count(*) from habit_checkins where habit_id=?",Integer.class,id)).isEqualTo(1);
        }
    }
    @Test void eventsAtExclusiveCutoffAreExcludedAndCurrentStateIncludesEqualTimestamps() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));
            b.request("PUT",p+"/completions/2026-11-02",action(0),true);
            assertThat(firstDay(b,p).get("completed").asBoolean()).isTrue();
            var historical=get(b,p+"/history?from=2026-11-02&to=2026-11-02&cutoff="+now.get());
            assertThat(historical.get("days").get("items").get(0).get("completed").asBoolean()).isFalse();
            assertThat(historical.get("actions").get("totalElements").asLong()).isZero();
        }
    }
    @Test void historyIncludesUnscheduledDatesAndSupportsIndependentPagination() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            var result=get(b,p+"/history?from=2026-11-01&to=2026-11-08&size=2&page=1&actionPage=3&schedulePage=3");
            assertThat(result.get("days").get("totalElements").asLong()).isEqualTo(8);assertThat(result.get("days").get("items").size()).isEqualTo(2);
            assertThat(result.get("actions").get("items").size()).isZero();
            assertThat(get(b,p+"/history?from=2026-11-01&to=2026-11-01").get("days").get("items").get(0).get("scheduled").asBoolean()).isFalse();
            assertThat(b.request("GET",p+"/history?from=2026-11-02&to=2026-11-02&cutoff=2099-01-01T00:00:00Z",null,false).statusCode()).isEqualTo(400);
        }
    }
    @Test void foreignHabitEndpointsAllReturnNotFound() throws Exception {
        try(var a=signedIn();var b=signedIn()) {
            String p=path(created(a));
            assertThat(b.request("GET",p,null,false).statusCode()).isEqualTo(404);
            assertThat(b.request("GET",p+"/history?from=2026-11-02&to=2026-11-03",null,false).statusCode()).isEqualTo(404);
            assertThat(b.request("PATCH",p,Map.of("version",0,"title","Other"),true).statusCode()).isEqualTo(404);
            assertThat(b.request("POST",p+"/schedules",schedule(0,"2026-11-03",List.of(2)),true).statusCode()).isEqualTo(404);
            assertThat(b.request("POST",p+"/archive",Map.of("version",0),true).statusCode()).isEqualTo(404);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(404);
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true).statusCode()).isEqualTo(404);
            assertThat(delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"0\"",true).statusCode()).isEqualTo(404);
            assertThat(delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"0\"",true).statusCode()).isEqualTo(404);
        }
    }
    @Test void allMutationsRequireCsrfAndPrivateReadsRequireAuthentication() throws Exception {
        try(var b=signedIn();var anonymous=browser()) {
            String p=path(created(b));
            assertThat(anonymous.request("GET","/api/v1/habits",null,false).statusCode()).isEqualTo(401);
            assertThat(anonymous.request("GET",p,null,false).statusCode()).isEqualTo(401);
            assertThat(b.request("POST","/api/v1/habits",input(),false).statusCode()).isEqualTo(403);
            assertThat(b.request("PATCH",p,Map.of("version",0,"title","Other"),false).statusCode()).isEqualTo(403);
            assertThat(b.request("POST",p+"/schedules",schedule(0,"2026-11-03",List.of(2)),false).statusCode()).isEqualTo(403);
            assertThat(b.request("POST",p+"/archive",Map.of("version",0),false).statusCode()).isEqualTo(403);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),false).statusCode()).isEqualTo(403);
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),false).statusCode()).isEqualTo(403);
            assertThat(delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"0\"",false).statusCode()).isEqualTo(403);
            assertThat(delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"0\"",false).statusCode()).isEqualTo(403);
            assertThat(b.request("POST",p+"/internal",null,true).statusCode()).isEqualTo(403);
        }
    }
    @Test void concurrentCompletionsProduceOneOccurrenceAndOneEvent() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));var barrier=new CyclicBarrier(2);
            Callable<Integer> send=() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode();};
            var a=pool.submit(send);var c=pool.submit(send);
            assertThat(new int[]{a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
            assertThat(history(b,p).get("actions").get("totalElements").asLong()).isEqualTo(1);assertThat(version(b,p)).isEqualTo(1);
        }
    }
    @Test void concurrentScheduleAndArchiveCannotLoseUpdates() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/schedules",schedule(0,"2026-11-03",List.of(2)),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/archive",Map.of("version",0),true).statusCode();});
            assertThat(new int[]{a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
            assertThat(version(b,p)).isEqualTo(1);
        }
    }
    @Test void concurrentRestCancellationAndScheduleEditCannotLoseUpdates() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true);var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/schedules",schedule(1,"2026-11-03",List.of(3)),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"1\"",true).statusCode();});
            assertThat(new int[]{a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void failedEventInsertionRollsBackFlushedStateAndVersion() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            doAnswer(call -> {entityManager.flush();throw new DataIntegrityViolationException("synthetic event failure");}).when(events).save(any());
            var response=b.request("PUT",p+"/completions/2026-11-02",action(0),true);
            assertThat(response.statusCode()).isEqualTo(409);assertThat(response.body()).doesNotContain("synthetic","SQL","constraint");
            assertThat(version(b,p)).isZero();assertThat(firstDay(b,p).get("completed").asBoolean()).isFalse();
            UUID id=UUID.fromString(p.substring(p.lastIndexOf('/')+1));
            assertThat(jdbc.queryForObject("select count(*) from habit_checkins where habit_id=?",Integer.class,id)).isZero();
        }
    }
    @Test void historySnapshotDoesNotMixConcurrentCommits() throws Exception {
        try(var b=signedIn();var pool=Executors.newSingleThreadExecutor()) {
            var h=created(b);String p=path(h);UUID id=UUID.fromString(h.get("id").asString());
            var readSchedule=new CountDownLatch(1);var committed=new CountDownLatch(1);
            doAnswer(call -> {
                Object result=mockingDetails(schedules).getMockCreationSettings().getDefaultAnswer().answer(call);
                readSchedule.countDown();if(!committed.await(10,TimeUnit.SECONDS)) throw new AssertionError("Writer did not commit");return result;
            }).when(schedules).effective(eq(id),any(),any(),any(),anyBoolean(),any());
            try {
                var reader=pool.submit(() -> history(b,p));assertThat(readSchedule.await(10,TimeUnit.SECONDS)).isTrue();
                // Avoid the writer's response calling the same spy while the read is paused.
                reset(schedules);
                assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(200);
                committed.countDown();var result=reader.get(10,TimeUnit.SECONDS);
                assertThat(result.get("days").get("items").get(0).get("completed").asBoolean()).isFalse();
                assertThat(result.get("actions").get("totalElements").asLong()).isZero();
            } finally { committed.countDown();reset(schedules); }
            assertThat(firstDay(b,p).get("completed").asBoolean()).isTrue();
        }
    }
    @Test void frozenTimezoneWinsNearWeekBoundaryAndHistoryUsesDst() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);
            now.set(Instant.parse("2026-11-08T18:59:59Z"));
            assertThat(b.request("PUT",p+"/completions/2026-11-08",action(0),true).statusCode()).isEqualTo(200);
            var row=history(b,p).get("days").get("items").get(6);
            assertThat(row.get("reportingDay").get("week").get("timezone").asString()).isEqualTo("Asia/Almaty");
            assertThat(row.get("completed").asBoolean()).isTrue();
        }
        try(var b=signedIn()) {
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/New_York"),true);
            now.set(Instant.parse("2026-03-02T12:00:00Z"));
            var body=new HashMap<>(input());body.put("startDate","2026-03-02");var r=b.request("POST","/api/v1/habits",body,true);
            String p=path(mapper.readTree(r.body()));
            var row=get(b,p+"/history?from=2026-03-08&to=2026-03-08").get("days").get("items").get(0).get("reportingDay");
            assertThat(Duration.between(Instant.parse(row.get("startsAt").asString()),Instant.parse(row.get("endsAt").asString())).toHours()).isEqualTo(23);
        }
    }
    @Test void operationTimeStaysBeforeCutoffEvenIfLaterClockReadsCrossIt() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));var reads=new java.util.concurrent.atomic.AtomicInteger();Instant end=Instant.parse("2026-11-08T19:00:00Z");
            when(clock.instant()).thenAnswer(call -> reads.getAndIncrement()==0 ? end.minusNanos(1000) : end);
            var response=b.request("PUT",p+"/completions/2026-11-08",action(0),true);assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
            when(clock.instant()).thenReturn(end);
            var actions=history(b,p).get("actions").get("items");
            assertThat(actions.get(0).get("acceptedAt").asString()).isEqualTo(end.minusNanos(1000).toString());
        }
    }
    @Test void noOpRestCancellationDoesNotFreezeAFutureWeek() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            assertThat(delete(b,p+"/rest-days/2026-11-16?weekStart=2026-11-16","\"0\"",true).statusCode()).isEqualTo(200);
            assertThat(version(b,p)).isZero();
            // If the no-op had reserved that week in Almaty, this transition would conflict.
            assertThat(b.request("PATCH","/api/v1/me",Map.of("timezone","UTC"),true).statusCode()).isEqualTo(200);
        }
    }
    @Test void asOfFutureDaysDoNotLeakLaterTimezonePolicy() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));Instant cutoff=now.get();
            String query=p+"/history?from=2026-11-09&to=2026-11-09&cutoff="+cutoff;
            var before=get(b,query).get("days").get("items").get(0);
            now.set(now.get().plusSeconds(1));
            assertThat(b.request("PATCH","/api/v1/me",Map.of("timezone","UTC"),true).statusCode()).isEqualTo(200);
            var after=get(b,query).get("days").get("items").get(0);
            assertThat(before.get("reportingDay").isNull()).isTrue();assertThat(after.get("reportingDay").isNull()).isTrue();
            assertThat(after.get("started").asBoolean()).isFalse();assertThat(after.get("schedule")).isEqualTo(before.get("schedule"));
        }
    }
    @Test void historicalRestStateSurvivesLaterCancellation() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true).statusCode()).isEqualTo(200);
            now.set(now.get().plusSeconds(1));Instant cutoff=now.get();now.set(now.get().plusSeconds(1));
            assertThat(delete(b,p+"/rest-days/2026-11-03?weekStart=2026-11-02","\"1\"",true).statusCode()).isEqualTo(200);
            var row=get(b,p+"/history?from=2026-11-03&to=2026-11-03&cutoff="+cutoff).get("days").get("items").get(0);
            assertThat(row.get("restPlanned").asBoolean()).isTrue();assertThat(row.get("restReason").asString()).isEqualTo("Travel");
            assertThat(history(b,p).get("days").get("items").get(1).get("restPlanned").asBoolean()).isFalse();
        }
    }
    @Test void completeVersusUndoIsLinearizableAndStaleRetryCannotReverseUndo() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));b.request("PUT",p+"/completions/2026-11-02",action(0),true);var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-02",action(1),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return delete(b,p+"/completions/2026-11-02?weekStart=2026-11-02","\"1\"",true).statusCode();});
            assertThat(a.get(15,TimeUnit.SECONDS)).isIn(200,409);assertThat(c.get(15,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(firstDay(b,p).get("completed").asBoolean()).isFalse();
            assertThat(history(b,p).get("actions").get("totalElements").asLong()).isEqualTo(2);
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(1),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void completionVersusRestCannotExemptAlreadyStartedWork() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/rest-days/2026-11-02",rest(0),true).statusCode();});
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(200);assertThat(c.get(15,TimeUnit.SECONDS)).isEqualTo(409);
            var day=firstDay(b,p);assertThat(day.get("completed").asBoolean()).isTrue();assertThat(day.get("restPlanned").asBoolean()).isFalse();
        }
    }
    @Test void profileTimezoneAndCheckinShareCalendarLock() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PATCH","/api/v1/me",Map.of("timezone","UTC"),true).statusCode();});
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(200);assertThat(c.get(15,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(firstDay(b,p).get("reportingDay").get("week").get("timezone").asString()).isEqualTo("Asia/Almaty");
        }
    }
    @Test void archiveRacingHistoricalReadCannotChangeEarlierCutoff() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));Instant cutoff=now.get();now.set(now.get().plusSeconds(1));
            var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return get(b,p+"/history?from=2026-11-03&to=2026-11-03&cutoff="+cutoff);});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/archive",Map.of("version",0),true).statusCode();});
            assertThat(c.get(15,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(a.get(15,TimeUnit.SECONDS).get("days").get("items").get(0).get("scheduled").asBoolean()).isTrue();
        }
    }
    @Test void differentOccurrencesShareHabitVersionAndCanBeRetriedSafely() throws Exception {
        try(var b=signedIn();var pool=Executors.newFixedThreadPool(2)) {
            String p=path(created(b));now.set(Instant.parse("2026-11-03T12:00:00Z"));var barrier=new CyclicBarrier(2);
            var a=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode();});
            var c=pool.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return b.request("PUT",p+"/completions/2026-11-03",action(0),true).statusCode();});
            int first=a.get(15,TimeUnit.SECONDS),second=c.get(15,TimeUnit.SECONDS);
            assertThat(new int[]{first,second}).containsExactlyInAnyOrder(200,409);
            String retry=first==409 ? "2026-11-02" : "2026-11-03";
            assertThat(b.request("PUT",p+"/completions/"+retry,action(1),true).statusCode()).isEqualTo(200);
            var days=history(b,p).get("days").get("items");assertThat(days.get(0).get("completed").asBoolean()).isTrue();assertThat(days.get(1).get("completed").asBoolean()).isTrue();
        }
    }
    @Test void inclusiveEndDateAndMissingOccurrencesStillDefineDailyObligations() throws Exception {
        try(var b=signedIn()) {
            var body=new HashMap<>(input());body.put("endDate","2026-11-03");
            var created=b.request("POST","/api/v1/habits",body,true);assertThat(created.statusCode()).isEqualTo(201);
            String p=path(mapper.readTree(created.body()));now.set(Instant.parse("2026-11-04T12:00:00Z"));
            var days=history(b,p).get("days").get("items");
            assertThat(days.get(0).get("eligible").asBoolean()).isTrue();assertThat(days.get(1).get("eligible").asBoolean()).isTrue();
            assertThat(days.get(2).get("scheduled").asBoolean()).isFalse();
            assertThat(b.request("PUT",p+"/completions/2026-11-03",action(0),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/completions/2026-11-04",action(1),true).statusCode()).isEqualTo(409);
        }
    }

    @Test void detailDoesNotAdvertiseAnAlreadySupersededFutureRevision() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));
            assertThat(b.request("POST",p+"/schedules",schedule(0,"2026-11-05",List.of(4)),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/schedules",schedule(1,"2026-11-03",List.of(2)),true).statusCode()).isEqualTo(200);
            now.set(Instant.parse("2026-11-04T12:00:00Z"));
            assertThat(get(b,p).get("latestPendingSchedule").isNull()).isTrue();
            assertThat(history(b,p).get("schedules").get("totalElements").asLong()).isEqualTo(3);
        }
    }

    @Test void subMicrosecondExclusiveCutoffDoesNotLosePersistedEvents() throws Exception {
        try(var b=signedIn()) {
            String p=path(created(b));now.set(now.get().plusSeconds(1));
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(200);
            Instant afterEvent=now.get().plusNanos(1);now.set(now.get().plusSeconds(1));
            var result=get(b,p+"/history?from=2026-11-02&to=2026-11-02&cutoff="+afterEvent);
            assertThat(result.get("days").get("items").get(0).get("completed").asBoolean()).isTrue();
            assertThat(result.get("actions").get("totalElements").asLong()).isEqualTo(1);
        }
    }

    @Test void futureStartCreatesNoEarlierObligationButCanHaveProspectiveRest() throws Exception {
        try(var b=signedIn()) {
            var body=new HashMap<>(input());body.put("startDate","2026-11-03");
            var response=b.request("POST","/api/v1/habits",body,true);assertThat(response.statusCode()).isEqualTo(201);
            String p=path(mapper.readTree(response.body()));
            assertThat(firstDay(b,p).get("scheduled").asBoolean()).isFalse();
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/rest-days/2026-11-03",rest(0),true).statusCode()).isEqualTo(200);
        }
    }
    @Test void weekendWeekdaysAndUnscheduledStartedDatesAreDistinct() throws Exception {
        try(var b=signedIn()) {
            var body=new HashMap<>(input());body.put("weekdays",List.of(6,7));
            var response=b.request("POST","/api/v1/habits",body,true);assertThat(response.statusCode()).isEqualTo(201);
            String p=path(mapper.readTree(response.body()));
            now.set(Instant.parse("2026-11-08T12:00:00Z"));
            assertThat(b.request("PUT",p+"/completions/2026-11-02",action(0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/completions/2026-11-07",action(0),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/completions/2026-11-08",action(1),true).statusCode()).isEqualTo(200);
            var days=history(b,p).get("days").get("items");
            assertThat(days.get(5).get("completed").asBoolean()).isTrue();assertThat(days.get(6).get("completed").asBoolean()).isTrue();
            assertThat(days.get(4).get("eligible").asBoolean()).isFalse();
        }
    }

}
