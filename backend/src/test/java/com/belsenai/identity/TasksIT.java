package com.belsenai.identity;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.belsenai.tasks.TaskEvent;
import com.belsenai.tasks.TaskEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("local")
class TasksIT extends PostgresHttpSupport {
    @MockitoBean Clock clock;
    @MockitoSpyBean TaskEventRepository eventRepository;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager entityManager;
    final AtomicReference<Instant> now=new AtomicReference<>();
    @BeforeEach void time() {
        now.set(Instant.parse("2026-11-02T12:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
    }
    Browser signedIn() throws Exception {
        Browser b=browser(); b.csrf();
        assertThat(b.request("POST","/api/v1/auth/register",registration(email()),true).statusCode()).isEqualTo(201);
        b.csrf(); return b;
    }
    JsonNode create(Browser b,Map<String,Object> input) throws Exception {
        var response=b.request("POST","/api/v1/tasks",input,true);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        return mapper.readTree(response.body());
    }
    JsonNode create(Browser b) throws Exception { return create(b,Map.of("title","Read Java")); }
    String path(JsonNode t) { return "/api/v1/tasks/"+t.get("id").asString(); }
    UUID id(JsonNode t) { return UUID.fromString(t.get("id").asString()); }
    JsonNode get(Browser b,String path) throws Exception {
        var response=b.request("GET",path,null,false); assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    JsonNode action(Browser b,String path,String action,long version) throws Exception {
        var response=b.request("POST",path+"/"+action,Map.of("version",version),true);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    long count(UUID task) { return jdbc.queryForObject("select count(*) from task_events where task_id=?",Long.class,task); }
    String goal(Browser b) throws Exception {
        var response=b.request("POST","/api/v1/goals",Map.of("title","Read","target",20,"unit","pages"),true);
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body()).get("id").asString();
    }

    @Test void createRetrieveOptionalFieldsAndCreationEvent() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);var loaded=get(b,path(t));
            assertThat(loaded.get("status").asString()).isEqualTo("OPEN");
            assertThat(loaded.get("currentDueDate").isNull()).isTrue();
            assertThat(loaded.get("originalDueDate").isNull()).isTrue();
            assertThat(loaded.get("estimatedDurationMinutes").isNull()).isTrue();
            assertThat(loaded.get("createdAt").asString()).isEqualTo(now.get().toString());
            assertThat(count(id(t))).isEqualTo(1);
            assertThat(jdbc.queryForObject("select type from task_events where task_id=?",String.class,id(t))).isEqualTo("CREATED");
        }
    }
    @Test void invalidTitlesDurationsAndUnknownOwnersAreRejected() throws Exception {
        try(var b=signedIn()) {
            for(Object duration:List.of(0,-1,1441)) {
                assertThat(b.request("POST","/api/v1/tasks",Map.of("title","Read","estimatedDurationMinutes",duration),true).statusCode()).isEqualTo(400);
            }
            assertThat(b.request("POST","/api/v1/tasks",Map.of("title"," "),true).statusCode()).isEqualTo(400);
            assertThat(b.request("POST","/api/v1/tasks",Map.of("title","Read","ownerId",UUID.randomUUID()),true).statusCode()).isEqualTo(400);
            assertThat(b.request("POST","/api/v1/tasks",Map.of("title","x".repeat(201)),true).statusCode()).isEqualTo(400);
        }
    }
    @Test void creationWithDatePreservesAssignmentAndContext() throws Exception {
        try(var b=signedIn()) {
            var t=create(b,Map.of("title","Read","dueDate","2026-11-04","estimatedDurationMinutes",30));
            assertThat(t.get("originalDueDate").asString()).isEqualTo("2026-11-04");
            assertThat(t.get("originalReportingWeek").get("timezone").asString()).isEqualTo("Asia/Almaty");
            assertThat(jdbc.queryForList("select type from task_events where task_id=? order by sequence",String.class,id(t)))
                    .containsExactly("CREATED","DUE_ASSIGNED");
        }
    }
    @Test void completionReopenAndMatchingVersionRepeatsAreIdempotent() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);
            var open=action(b,p,"reopen",t.get("version").asLong());
            assertThat(open.get("version").asLong()).isEqualTo(t.get("version").asLong());
            var completed=action(b,p,"complete",open.get("version").asLong());
            assertThat(completed.get("completedAt").isNull()).isFalse();
            var repeat=action(b,p,"complete",completed.get("version").asLong());
            assertThat(repeat.get("version")).isEqualTo(completed.get("version"));
            assertThat(count(id(t))).isEqualTo(2);
            var reopened=action(b,p,"reopen",repeat.get("version").asLong());
            assertThat(reopened.get("status").asString()).isEqualTo("OPEN");
            assertThat(reopened.get("completedAt").isNull()).isTrue();
            assertThat(count(id(t))).isEqualTo(3);
        }
    }
    @Test void staleRepeatedTransitionCannotHideConcurrentChange() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);action(b,p,"complete",t.get("version").asLong());
            assertThat(b.request("POST",p+"/complete",Map.of("version",t.get("version").asLong()),true).statusCode()).isEqualTo(409);
            assertThat(count(id(t))).isEqualTo(2);
        }
    }
    @Test void reschedulingCompletedTaskRequiresReopening() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);var completed=action(b,p,"complete",0);
            assertThat(b.request("POST",p+"/reschedule",Map.of("version",completed.get("version").asLong(),"dueDate","2026-11-04"),true).statusCode()).isEqualTo(409);
            assertThat(count(id(t))).isEqualTo(2);
        }
    }
    @Test void firstDateRescheduleAndRemovalRetainOriginalDate() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);
            var assigned=b.request("POST",p+"/reschedule",Map.of("version",0,"dueDate","2026-11-04"),true);
            assertThat(assigned.statusCode()).isEqualTo(200);
            var state=mapper.readTree(assigned.body());
            state=mapper.readTree(b.request("POST",p+"/reschedule",Map.of("version",state.get("version").asLong(),"dueDate","2026-11-10"),true).body());
            var removed=b.request("POST",p+"/reschedule","{\"version\":"+state.get("version").asLong()+",\"dueDate\":null}",true);
            assertThat(removed.statusCode()).isEqualTo(200);state=mapper.readTree(removed.body());
            assertThat(state.get("currentDueDate").isNull()).isTrue();
            assertThat(state.get("originalDueDate").asString()).isEqualTo("2026-11-04");
            assertThat(jdbc.queryForList("select type from task_events where task_id=? order by sequence",String.class,id(t)))
                    .containsExactly("CREATED","DUE_ASSIGNED","RESCHEDULED","RESCHEDULED");
        }
    }
    @Test void firstDateInClosedWeekFailsWithoutTaskOrEventChanges() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);
            assertThat(b.request("POST",p+"/reschedule",Map.of("version",0,"dueDate","2026-10-25"),true).statusCode()).isEqualTo(409);
            assertThat(get(b,p).get("originalDueDate").isNull()).isTrue();assertThat(count(id(t))).isEqualTo(1);
            assertThat(b.request("POST","/api/v1/tasks",Map.of("title","Past","dueDate","2026-10-25"),true).statusCode()).isEqualTo(409);
            assertThat(get(b,"/api/v1/tasks").get("totalElements").asLong()).isEqualTo(1);
        }
    }
    @Test void equalTimestampEventsHaveDeterministicSequence() throws Exception {
        try(var b=signedIn()) {
            var t=create(b,Map.of("title","Read","dueDate","2026-11-04"));String p=path(t);
            var state=action(b,p,"complete",t.get("version").asLong());state=action(b,p,"reopen",state.get("version").asLong());
            action(b,p,"archive",state.get("version").asLong());
            assertThat(jdbc.queryForList("select sequence from task_events where task_id=? order by sequence",Long.class,id(t))).containsExactly(1L,2L,3L,4L,5L);
            assertThat(jdbc.queryForObject("select count(distinct occurred_at) from task_events where task_id=?",Integer.class,id(t))).isEqualTo(1);
            assertThat(jdbc.queryForList("select status from task_events where task_id=? order by sequence",String.class,id(t))).containsExactly("OPEN","OPEN","COMPLETED","OPEN","OPEN");
        }
    }
    @Test void eventsCannotBeUpdatedOrDeleted() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);
            assertThatThrownBy(() -> jdbc.update("update task_events set status='COMPLETED' where task_id=?",id(t))).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("delete from task_events where task_id=?",id(t))).isInstanceOf(DataAccessException.class);
            assertThat(count(id(t))).isEqualTo(1);
        }
    }
    @Test void eventFailureRollsBackAlreadyFlushedTaskUpdate() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);
            doAnswer(call -> {entityManager.flush();throw new DataIntegrityViolationException("synthetic-event-failure");})
                    .when(eventRepository).save(any(TaskEvent.class));
            var failed=b.request("POST",p+"/complete",Map.of("version",0),true);
            assertThat(failed.statusCode()).isEqualTo(409);
            assertThat(failed.body()).doesNotContain("synthetic-event-failure","SQL","Exception");
            var unchanged=get(b,p);assertThat(unchanged.get("status").asString()).isEqualTo("OPEN");
            assertThat(unchanged.get("version").asLong()).isZero();assertThat(count(id(t))).isEqualTo(1);
        }
    }
    @Test void archivedTasksAreReadableButImmutableAndKeepCompletion() throws Exception {
        try(var b=signedIn()) {
            var t=create(b);String p=path(t);var completed=action(b,p,"complete",0);
            var archived=action(b,p,"archive",completed.get("version").asLong());long version=archived.get("version").asLong();
            assertThat(archived.get("status").asString()).isEqualTo("COMPLETED");
            assertThat(archived.get("completedAt").isNull()).isFalse();
            for(String command:List.of("complete","reopen")) {
                assertThat(b.request("POST",p+"/"+command,Map.of("version",version),true).statusCode()).isEqualTo(409);
            }
            assertThat(b.request("PATCH",p,Map.of("version",version,"title","Changed"),true).statusCode()).isEqualTo(409);
            action(b,p,"archive",version);assertThat(count(id(t))).isEqualTo(3);
            assertThat(get(b,"/api/v1/tasks").get("totalElements").asLong()).isZero();
            assertThat(get(b,"/api/v1/tasks?archived=true").get("totalElements").asLong()).isEqualTo(1);
        }
    }
    @Test void goalLinkingIsOwnedActiveAndCompletionDoesNotAddProgress() throws Exception {
        try(var a=signedIn();var b=signedIn()) {
            String ownedGoal=goal(a);String foreignGoal=goal(b);
            assertThat(a.request("POST","/api/v1/tasks",Map.of("title","Read","goalId",foreignGoal),true).statusCode()).isEqualTo(404);
            var t=create(a,Map.of("title","Read","goalId",ownedGoal));
            assertThat(a.request("PATCH",path(t),Map.of("version",0,"goalId",foreignGoal),true).statusCode()).isEqualTo(404);
            action(a,path(t),"complete",0);
            assertThat(get(a,"/api/v1/goals/"+ownedGoal).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("0");
            a.request("POST","/api/v1/goals/"+ownedGoal+"/archive",Map.of("version",0),true);
            assertThat(a.request("POST","/api/v1/tasks",Map.of("title","New","goalId",ownedGoal),true).statusCode()).isEqualTo(409);
            assertThat(get(a,path(t)).get("archivedAt").isNull()).isTrue();
            assertThat(a.request("GET","/api/v1/tasks?goalId="+foreignGoal,null,false).statusCode()).isEqualTo(404);
        }
    }
    @Test void crossUserReadsAndEveryMutationReturnNotFound() throws Exception {
        try(var a=signedIn();var b=signedIn()) {
            var t=create(a);String p=path(t);
            assertThat(b.request("GET",p,null,false).statusCode()).isEqualTo(404);
            assertThat(b.request("PATCH",p,Map.of("version",0,"title","Attack"),true).statusCode()).isEqualTo(404);
            for(String action:List.of("complete","reopen","archive")) {
                assertThat(b.request("POST",p+"/"+action,Map.of("version",0),true).statusCode()).isEqualTo(404);
            }
            assertThat(b.request("POST",p+"/reschedule",Map.of("version",0,"dueDate","2026-11-04"),true).statusCode()).isEqualTo(404);
            assertThat(get(b,"/api/v1/tasks").get("totalElements").asLong()).isZero();
        }
    }
    @Test void paginationFiltersAndStableOrdering() throws Exception {
        try(var b=signedIn()) {
            String goal=goal(b);
            create(b,Map.of("title","A","dueDate","2026-11-03","goalId",goal));
            var completed=create(b,Map.of("title","B","dueDate","2026-11-05"));action(b,path(completed),"complete",completed.get("version").asLong());create(b);
            assertThat(get(b,"/api/v1/tasks?dueDate=2026-11-03").get("totalElements").asLong()).isEqualTo(1);
            assertThat(get(b,"/api/v1/tasks?from=2026-11-03&to=2026-11-05").get("totalElements").asLong()).isEqualTo(2);
            assertThat(get(b,"/api/v1/tasks?status=COMPLETED").get("totalElements").asLong()).isEqualTo(1);
            assertThat(get(b,"/api/v1/tasks?goalId="+goal).get("totalElements").asLong()).isEqualTo(1);
            assertThat(get(b,"/api/v1/tasks?size=1&page=0").get("items").get(0).get("id"))
                    .isNotEqualTo(get(b,"/api/v1/tasks?size=1&page=1").get("items").get(0).get("id"));
            for(String query:List.of("size=101","page=-1","status=UNKNOWN","from=2026-11-05&to=2026-11-02","dueDate=2026-11-03&from=2026-11-02")) {
                assertThat(b.request("GET","/api/v1/tasks?"+query,null,false).statusCode()).isEqualTo(400);
            }
        }
    }
    @Test void patchOptionalFieldsClearWithoutChangingScheduling() throws Exception {
        try(var b=signedIn()) {
            String goal=goal(b);var t=create(b,Map.of("title","Read","description","Details","goalId",goal,"estimatedDurationMinutes",30));String p=path(t);
            var changed=b.request("PATCH",p,"{\"version\":0,\"description\":null,\"goalId\":null,\"estimatedDurationMinutes\":null}",true);
            assertThat(changed.statusCode()).isEqualTo(200);var state=mapper.readTree(changed.body());
            assertThat(state.get("description").isNull()).isTrue();assertThat(state.get("goalId").isNull()).isTrue();assertThat(state.get("estimatedDurationMinutes").isNull()).isTrue();
            assertThat(count(id(t))).isEqualTo(2);
            assertThat(b.request("PATCH",p,Map.of("version",state.get("version").asLong(),"currentDueDate","2026-11-03"),true).statusCode()).isEqualTo(400);
        }
    }
    @Test void csrfSessionContinuityAndOnlyImplementedRoutesAuthorized() throws Exception {
        try(var anon=browser();var b=signedIn()) {
            assertThat(anon.request("GET","/api/v1/tasks",null,false).statusCode()).isEqualTo(401);
            assertThat(b.request("POST","/api/v1/tasks",Map.of("title","Read"),false).statusCode()).isEqualTo(403);
            var t=create(b);String p=path(t);
            for(String command:List.of("complete","reopen","archive","reschedule")) {
                assertThat(b.request("POST",p+"/"+command,Map.of("version",0),false).statusCode()).isEqualTo(403);
            }
            assertThat(b.request("PATCH",p,Map.of("version",0,"title","Read"),false).statusCode()).isEqualTo(403);
            assertThat(b.request("GET",p+"/events",null,false).statusCode()).isEqualTo(403);
            assertThat(b.request("GET","/api/v1/me",null,false).statusCode()).isEqualTo(200);
        }
    }
    @Test void simultaneousCompletionHasOneEventAndOneStaleConflict() throws Exception {
        try(var b=signedIn();var executor=Executors.newFixedThreadPool(2)) {
            var t=create(b);String p=path(t);var start=new CyclicBarrier(2);
            Callable<Integer> complete=() -> {start.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/complete",Map.of("version",0),true).statusCode();};
            var first=executor.submit(complete);var second=executor.submit(complete);
            assertThat(new int[]{first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
            assertThat(count(id(t))).isEqualTo(2);
        }
    }
    @Test void simultaneousMetadataChangesDoNotSilentlyOverwrite() throws Exception {
        try(var b=signedIn();var executor=Executors.newFixedThreadPool(2)) {
            var t=create(b);String p=path(t);var start=new CyclicBarrier(2);
            var first=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("PATCH",p,Map.of("version",0,"title","First"),true).statusCode();});
            var second=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("PATCH",p,Map.of("version",0,"title","Second"),true).statusCode();});
            assertThat(new int[]{first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
            assertThat(count(id(t))).isEqualTo(2);
            assertThat(get(b,p).get("version").asLong()).isEqualTo(1);
        }
    }
    @Test void completionImmediatelyBeforeCutoffHasConsistentEventTimestamp() throws Exception {
        try(var b=signedIn()) {
            var t=create(b,Map.of("title","Read","dueDate","2026-11-02"));String p=path(t);
            Instant cutoff=Instant.parse("2026-11-08T19:00:00Z");now.set(cutoff.minusMillis(1));
            var completed=action(b,p,"complete",t.get("version").asLong());
            var event=jdbc.queryForMap("select occurred_at,completed_at from task_events where task_id=? order by sequence desc limit 1",id(t));
            assertThat(event.get("occurred_at")).isEqualTo(event.get("completed_at"));
            assertThat(Instant.parse(completed.get("completedAt").asString())).isBefore(cutoff);
            now.set(cutoff);
            action(b,p,"reopen",completed.get("version").asLong());
            assertThat(jdbc.queryForObject("select status from task_events where task_id=? and occurred_at<? order by sequence desc limit 1",String.class,id(t),java.sql.Timestamp.from(cutoff))).isEqualTo("COMPLETED");
        }
    }

    @Test void frozenOriginalWeekSurvivesProfileTimezoneAndLaterReschedule() throws Exception {
        try(var b=signedIn()) {
            var t=create(b,Map.of("title","Read","dueDate","2026-11-02"));String p=path(t);
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);
            now.set(Instant.parse("2026-11-09T09:00:00Z"));
            var response=b.request("POST",p+"/reschedule",Map.of("version",t.get("version").asLong(),"dueDate","2026-11-10"),true);
            assertThat(response.statusCode()).isEqualTo(200);var updated=mapper.readTree(response.body());
            assertThat(updated.get("originalDueDate").asString()).isEqualTo("2026-11-02");
            assertThat(updated.get("originalReportingWeek")).isEqualTo(t.get("originalReportingWeek"));
            assertThat(jdbc.queryForObject("select w.timezone from task_events e join reporting_weeks w on w.id=e.reporting_week_id where e.task_id=? order by e.sequence desc limit 1",String.class,id(t))).isEqualTo("America/Los_Angeles");
        }
    }
    @Test void completionStateAtCutoffSurvivesLaterReopenAndArchive() throws Exception {
        try(var b=signedIn()) {
            var t=create(b,Map.of("title","Read","dueDate","2026-11-02"));String p=path(t);
            now.set(Instant.parse("2026-11-03T12:00:00Z"));var state=action(b,p,"complete",t.get("version").asLong());
            now.set(Instant.parse("2026-11-09T12:00:00Z"));state=action(b,p,"reopen",state.get("version").asLong());
            now.set(Instant.parse("2026-11-10T12:00:00Z"));action(b,p,"archive",state.get("version").asLong());
            assertThat(jdbc.queryForObject("select status from task_events where task_id=? and occurred_at<? order by sequence desc limit 1",String.class,id(t),java.sql.Timestamp.from(Instant.parse("2026-11-08T19:00:00Z")))).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("select archived_at from task_events where task_id=? and occurred_at<? order by sequence desc limit 1",java.sql.Timestamp.class,id(t),java.sql.Timestamp.from(Instant.parse("2026-11-08T19:00:00Z")))).isNull();
        }
    }
    @Test void postgresChecksAndCompositeOwnershipRejectInvalidWrites() throws Exception {
        try(var a=signedIn();var b=signedIn()) {
            var t=create(a);UUID task=id(t);String foreign=goal(b);
            assertThatThrownBy(() -> jdbc.update("update tasks set goal_id=? where id=?",UUID.fromString(foreign),task)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update tasks set estimated_duration_minutes=0 where id=?",task)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update tasks set status='COMPLETED' where id=?",task)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into task_events(id,task_id,owner_id,sequence,type,occurred_at,reporting_week_id,status)
                    select ?,task_id,owner_id,sequence,type,occurred_at,reporting_week_id,status from task_events where task_id=?
                    """,UUID.randomUUID(),task)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
