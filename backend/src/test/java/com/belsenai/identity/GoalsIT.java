package com.belsenai.identity;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.belsenai.goals.GoalPlanRepository;

@ActiveProfiles("local")
class GoalsIT extends PostgresHttpSupport {
    @MockitoBean Clock clock;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean GoalPlanRepository planRepository;
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
    Map<String,Object> goal() { return Map.of("title","Study Java","target",20,"unit","sessions"); }
    JsonNode create(Browser b) throws Exception {
        var response=b.request("POST","/api/v1/goals",goal(),true);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        return mapper.readTree(response.body());
    }
    String path(JsonNode g) { return "/api/v1/goals/"+g.get("id").asString(); }
    Map<String,Object> entry(UUID key,Object amount,String date) {
        return Map.of("submissionId",key,"quantity",amount,"effectiveDate",date);
    }
    JsonNode get(Browser b,String path) throws Exception {
        var response=b.request("GET",path,null,false); assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    @Test void creationListAndLifetimeEmptyState() throws Exception {
        try(var b=signedIn()) {
            var g=create(b); assertThat(g.get("lifetimeUnits").decimalValue()).isEqualByComparingTo("0");
            assertThat(g.get("weeklyPlans").size()).isZero();
            assertThat(get(b,"/api/v1/goals").get("totalElements").asLong()).isEqualTo(1);
            assertThat(get(b,path(g)).get("status").asString()).isEqualTo("ACTIVE");
        }
    }
    @Test void invalidTargetsPrecisionBlankFieldsAndDatesRejected() throws Exception {
        try(var b=signedIn()) {
            for(Object amount:List.of(0,-1,"0.00001","1000000000000000")) {
                var input=new HashMap<>(goal()); input.put("target",amount);
                assertThat(b.request("POST","/api/v1/goals",input,true).statusCode()).isEqualTo(400);
            }
            var input=new HashMap<>(goal()); input.put("title"," ");
            assertThat(b.request("POST","/api/v1/goals",input,true).statusCode()).isEqualTo(400);
            input=new HashMap<>(goal()); input.put("startDate","2026-11-03"); input.put("deadline","2026-11-02");
            assertThat(b.request("POST","/api/v1/goals",input,true).statusCode()).isEqualTo(400);
        }
    }
    @Test void progressAndWeeklyProgressAreSeparateAndCapped() throws Exception {
        try(var b=signedIn()) {
            var g=create(b); String p=path(g);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",10),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),12,"2026-11-02"),true).statusCode()).isEqualTo(201);
            var result=get(b,p); assertThat(result.get("lifetimePercent").decimalValue()).isEqualByComparingTo("60");
            assertThat(result.get("weeklyPlans").get(0).get("weeklyPercent").decimalValue()).isEqualByComparingTo("100");
            b.request("POST",p+"/progress",entry(UUID.randomUUID(),15,"2026-11-02"),true);
            result=get(b,p); assertThat(result.get("lifetimeUnits").decimalValue()).isEqualByComparingTo("27");
            assertThat(result.get("lifetimePercent").decimalValue()).isEqualByComparingTo("100");
        }
    }
    @Test void identicalRetriesReturnOriginalAndConflictsDoNotIncreaseTotal() throws Exception {
        try(var b=signedIn()) {
            var g=create(b); String p=path(g); UUID key=UUID.randomUUID(); var input=entry(key,2,"2026-11-02");
            var first=b.request("POST",p+"/progress",input,true); var second=b.request("POST",p+"/progress",input,true);
            assertThat(first.statusCode()).isEqualTo(201); assertThat(second.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(second.body()).get("id")).isEqualTo(mapper.readTree(first.body()).get("id"));
            assertThat(b.request("POST",p+"/progress",entry(key,3,"2026-11-02"),true).statusCode()).isEqualTo(409);
            assertThat(get(b,p).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("2");
        }
    }
    @Test void concurrentDuplicateSubmissionsCreateExactlyOneEntry() throws Exception {
        try(var b=signedIn();var executor=Executors.newFixedThreadPool(2)) {
            var g=create(b); String p=path(g); UUID key=UUID.randomUUID(); var start=new CyclicBarrier(2);
            Callable<Integer> send=() -> {start.await(5,TimeUnit.SECONDS); return b.request("POST",p+"/progress",entry(key,2,"2026-11-02"),true).statusCode();};
            var a=executor.submit(send);var c=executor.submit(send);
            assertThat(new int[]{a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,201);
            assertThat(jdbc.queryForObject("select count(*) from goal_progress_entries where goal_id=?",Integer.class,UUID.fromString(g.get("id").asString()))).isEqualTo(1);
            assertThat(get(b,p).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("2");
        }
    }
    @Test void correctionVoidsOriginalAndReplacementIsExplicit() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);UUID key=UUID.randomUUID();
            var first=b.request("POST",p+"/progress",entry(key,5,"2026-11-02"),true);
            String id=mapper.readTree(first.body()).get("id").asString();
            assertThat(b.request("POST",p+"/progress/"+id+"/void",null,true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/progress/"+id+"/void",null,true).statusCode()).isEqualTo(200);
            b.request("POST",p+"/progress",entry(UUID.randomUUID(),3,"2026-11-02"),true);
            assertThat(get(b,p).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("3");
            assertThat(get(b,p+"/progress").get("totalElements").asInt()).isEqualTo(2);
            assertThat(mapper.readTree(b.request("POST",p+"/progress",entry(key,5,"2026-11-02"),true).body()).get("voidedAt").isNull()).isFalse();
        }
    }
    @Test void invalidFutureAndClosedProgressRejectedWithoutWrites() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            for(Object amount:List.of(0,-1,"0.00001")) {
                assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),amount,"2026-11-02"),true).statusCode()).isEqualTo(400);
            }
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-03"),true).statusCode()).isEqualTo(400);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-10-26"),true).statusCode()).isEqualTo(409);
            assertThat(get(b,p+"/progress").get("totalElements").asInt()).isZero();
        }
    }
    @Test void archivedGoalsRejectNewProgressUpdatesAndCorrectionsButAllowRetry() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);UUID key=UUID.randomUUID();var input=entry(key,1,"2026-11-02");
            var e=mapper.readTree(b.request("POST",p+"/progress",input,true).body());
            var current=get(b,p);
            assertThat(b.request("POST",p+"/archive",Map.of("version",current.get("version").asLong()),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).statusCode()).isEqualTo(409);
            assertThat(b.request("POST",p+"/progress",input,true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/progress/"+e.get("id").asString()+"/void",null,true).statusCode()).isEqualTo(409);
            assertThat(b.request("PATCH",p,Map.of("version",get(b,p).get("version").asLong(),"title","New"),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void weeklyPlansFreezeOnStartAndRequireMondayAndPositiveAmount() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-03",Map.of("plannedUnits",2),true).statusCode()).isEqualTo(400);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",0),true).statusCode()).isEqualTo(400);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-10-26",Map.of("plannedUnits",2),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",2),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",3,"version",0),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",2),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",3,"version",0),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",4,"version",0),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void frozenWeekAndFuturePlanIgnoreLaterProfileTimezone() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",2),true);
            assertThat(b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true).statusCode()).isEqualTo(200);
            // It is Monday in the frozen Asia/Almaty week but still Sunday in Los Angeles.
            now.set(Instant.parse("2026-11-08T19:30:00Z"));
            var response=b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-09"),true);
            assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
            assertThat(mapper.readTree(response.body()).get("reportingWeek").get("timezone").asString()).isEqualTo("Asia/Almaty");
        }
    }
    @Test void closedWeekCannotBeReopenedByChangingTimezone() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            var e=mapper.readTree(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).body());
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);
            now.set(Instant.parse("2026-11-08T19:30:00Z"));
            assertThat(b.request("POST",p+"/progress/"+e.get("id").asString()+"/void",null,true).statusCode()).isEqualTo(409);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-08"),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void staleMetadataAndInvalidChangesRollback() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);long version=g.get("version").asLong();
            assertThat(b.request("PATCH",p,Map.of("version",version,"title","Updated"),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PATCH",p,Map.of("version",version,"target",30),true).statusCode()).isEqualTo(409);
            version=get(b,p).get("version").asLong();
            assertThat(b.request("PATCH",p,Map.of("version",version,"title","Should rollback","startDate","2026-11-03","deadline","2026-11-02"),true).statusCode()).isEqualTo(400);
            assertThat(get(b,p).get("title").asString()).isEqualTo("Updated");
        }
    }
    @Test void simultaneousMetadataUpdatesRejectOneStaleWriter() throws Exception {
        try(var b=signedIn();var executor=Executors.newFixedThreadPool(2)) {
            var g=create(b);String p=path(g);long version=g.get("version").asLong();
            var start=new CyclicBarrier(2);
            var first=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("PATCH",p,Map.of("version",version,"title","First"),true).statusCode();});
            var second=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("PATCH",p,Map.of("version",version,"title","Second"),true).statusCode();});
            assertThat(new int[]{first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200,409);
            assertThat(get(b,p).get("version").asLong()).isEqualTo(version+1);
        }
    }
    @Test void concurrentArchiveAndProgressCannotAdmitLaterWrites() throws Exception {
        try(var b=signedIn();var executor=Executors.newFixedThreadPool(2)) {
            var g=create(b);String p=path(g);var start=new CyclicBarrier(2);
            var archive=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/archive",Map.of("version",g.get("version").asLong()),true).statusCode();});
            var write=executor.submit(() -> {start.await(5,TimeUnit.SECONDS);return b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).statusCode();});
            assertThat(archive.get(15,TimeUnit.SECONDS)).isIn(200,409);
            assertThat(write.get(15,TimeUnit.SECONDS)).isIn(201,409);
            var state=get(b,p);
            if(state.get("status").asString().equals("ACTIVE")) {
                assertThat(b.request("POST",p+"/archive",Map.of("version",state.get("version").asLong()),true).statusCode()).isEqualTo(200);
            }
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).statusCode()).isEqualTo(409);
        }
    }

    @Test void unitIsFixedAfterProgressWhileTargetCanChange() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            b.request("POST",p+"/progress",entry(UUID.randomUUID(),2,"2026-11-02"),true);
            long v=get(b,p).get("version").asLong();
            assertThat(b.request("PATCH",p,Map.of("version",v,"unit","hours"),true).statusCode()).isEqualTo(409);
            assertThat(b.request("PATCH",p,Map.of("version",v,"target",4),true).statusCode()).isEqualTo(200);
            assertThat(get(b,p).get("lifetimePercent").decimalValue()).isEqualByComparingTo("50");
        }
    }
    @Test void crossUserGoalsHistoryAndCorrectionsAreNotFound() throws Exception {
        try(var a=signedIn();var b=signedIn()) {
            var g=create(a);String p=path(g);
            var e=mapper.readTree(a.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).body());
            assertThat(b.request("GET",p,null,false).statusCode()).isEqualTo(404);
            assertThat(b.request("GET",p+"/progress",null,false).statusCode()).isEqualTo(404);
            assertThat(b.request("PATCH",p,Map.of("version",0,"title","Attack"),true).statusCode()).isEqualTo(404);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true).statusCode()).isEqualTo(404);
            assertThat(b.request("POST",p+"/progress/"+e.get("id").asString()+"/void",null,true).statusCode()).isEqualTo(404);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",1),true).statusCode()).isEqualTo(404);
        }
    }
    @Test void securityPaginationAndUnknownFieldsRemainProtected() throws Exception {
        try(var anon=browser();var b=signedIn()) {
            assertThat(anon.request("GET","/api/v1/goals",null,false).statusCode()).isEqualTo(401);
            assertThat(b.request("POST","/api/v1/goals",goal(),false).statusCode()).isEqualTo(403);
            var input=new HashMap<>(goal());input.put("ownerId",UUID.randomUUID());
            assertThat(b.request("POST","/api/v1/goals",input,true).statusCode()).isEqualTo(400);
            assertThat(b.request("GET","/api/v1/goals?page=-1",null,false).statusCode()).isEqualTo(400);
            assertThat(b.request("GET","/api/v1/goals?size=101",null,false).statusCode()).isEqualTo(400);
            assertThat(b.request("GET","/api/v1/goals/not-a-uuid",null,false).statusCode()).isEqualTo(400);
        }
    }
    @Test void patchOmissionPreservesOptionalFieldsAndExplicitNullClearsThem() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            var set=b.request("PATCH",p,Map.of("version",0,"description","Description","deadline","2026-12-01"),true);
            assertThat(set.statusCode()).withFailMessage(set.body()).isEqualTo(200);
            long v=get(b,p).get("version").asLong();
            assertThat(b.request("PATCH",p,Map.of("version",v,"title","Changed"),true).statusCode()).isEqualTo(200);
            assertThat(get(b,p).get("description").asString()).isEqualTo("Description");
            v=get(b,p).get("version").asLong();
            assertThat(b.request("PATCH",p,"{\"version\":"+v+",\"description\":null,\"deadline\":null}",true).statusCode()).isEqualTo(200);
            assertThat(get(b,p).get("description").isNull()).isTrue();
            assertThat(get(b,p).get("deadline").isNull()).isTrue();
        }
    }
    @Test void newWeekAdoptsProfileTimezoneWithoutChangingPriorRecord() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true);
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);
            now.set(Instant.parse("2026-11-09T09:00:00Z"));
            var next=b.request("POST",p+"/progress",entry(UUID.randomUUID(),1,"2026-11-09"),true);
            assertThat(next.statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(next.body()).get("reportingWeek").get("timezone").asString()).isEqualTo("America/Los_Angeles");
            var history=get(b,p+"/progress").get("items");
            assertThat(history.get(0).get("reportingWeek").get("timezone").asString()).isEqualTo("Asia/Almaty");
        }
    }
    @Test void storedCalendarRespectsDaylightSavingBoundary() throws Exception {
        try(var b=signedIn()) {
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/New_York"),true);
            now.set(Instant.parse("2026-10-26T12:00:00Z"));
            var g=create(b);
            var response=b.request("PUT",path(g)+"/weekly-plans/2026-10-26",Map.of("plannedUnits",2),true);
            assertThat(response.statusCode()).isEqualTo(200);
            var week=mapper.readTree(response.body()).get("reportingWeek");
            assertThat(Duration.between(Instant.parse(week.get("startsAt").asString()),Instant.parse(week.get("endsAt").asString())).toHours()).isEqualTo(169);
        }
    }

    @Test void databaseChecksAndOwnerForeignKeysRejectInvalidRecords() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);UUID id=UUID.fromString(g.get("id").asString());
            assertThatThrownBy(() -> jdbc.update("update goals set target=0 where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update goals set title='' where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
            b.request("POST",path(g)+"/progress",entry(UUID.randomUUID(),1,"2026-11-02"),true);
            assertThatThrownBy(() -> jdbc.update("update goal_progress_entries set owner_id=? where goal_id=?",UUID.randomUUID(),id)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into goal_progress_entries (id,goal_id,owner_id,reporting_week_id,submission_id,quantity,effective_date,created_at)
                    select ?,goal_id,owner_id,reporting_week_id,submission_id,quantity,effective_date,created_at
                    from goal_progress_entries where goal_id=?
                    """,UUID.randomUUID(),id)).isInstanceOf(DataIntegrityViolationException.class);
            b.request("PUT",path(g)+"/weekly-plans/2026-11-02",Map.of("plannedUnits",2),true);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into goal_weekly_plans(id,goal_id,owner_id,reporting_week_id,planned_units,version,created_at,updated_at)
                    select ?,goal_id,owner_id,reporting_week_id,planned_units,version,created_at,updated_at
                    from goal_weekly_plans where goal_id=?
                    """,UUID.randomUUID(),id)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
    // The first read accepts the operation; any additional read observes the next week.
    void crossesBoundary(Instant before, Instant after) {
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        when(clock.instant()).thenAnswer(call -> reads.getAndIncrement()==0 ? before : after);
    }
    final Instant cutoff=Instant.parse("2026-11-08T19:00:00Z");

    @Test void recordingUsesOneAcceptanceInstantAcrossCutoffAndRetry() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);UUID key=UUID.randomUUID();
            crossesBoundary(cutoff.minusMillis(1),cutoff);
            var response=b.request("POST",p+"/progress",entry(key,2,"2026-11-08"),true);
            assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
            var saved=mapper.readTree(response.body());
            assertThat(saved.get("createdAt").asString()).isEqualTo(cutoff.minusMillis(1).toString());
            assertThat(saved.get("reportingWeek").get("endsAt").asString()).isEqualTo(cutoff.toString());
            when(clock.instant()).thenReturn(cutoff);
            assertThat(b.request("POST",p+"/progress",entry(key,2,"2026-11-08"),true).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),2,"2026-11-08"),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void voidUsesOneAcceptanceInstantAcrossCutoff() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            var saved=mapper.readTree(b.request("POST",p+"/progress",entry(UUID.randomUUID(),2,"2026-11-02"),true).body());
            crossesBoundary(cutoff.minusMillis(1),cutoff);
            var response=b.request("POST",p+"/progress/"+saved.get("id").asString()+"/void",null,true);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).get("voidedAt").asString()).isEqualTo(cutoff.minusMillis(1).toString());
            assertThat(get(b,p).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("0");
        }
    }
    @Test void existingPlanUpdateUsesAcceptanceBeforeStart() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",2),true).statusCode()).isEqualTo(200);
            crossesBoundary(cutoff.minusMillis(1),cutoff);
            var response=b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",3,"version",0),true);
            assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select updated_at from goal_weekly_plans where goal_id=?",java.sql.Timestamp.class,UUID.fromString(g.get("id").asString())).toInstant()).isEqualTo(cutoff.minusMillis(1));
            when(clock.instant()).thenReturn(cutoff);
            assertThat(b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",4,"version",1),true).statusCode()).isEqualTo(409);
        }
    }
    @Test void voidAtExclusiveEndIsRejected() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            var saved=mapper.readTree(b.request("POST",p+"/progress",entry(UUID.randomUUID(),2,"2026-11-02"),true).body());
            now.set(cutoff);
            assertThat(b.request("POST",p+"/progress/"+saved.get("id").asString()+"/void",null,true).statusCode()).isEqualTo(409);
            assertThat(get(b,p).get("lifetimeUnits").decimalValue()).isEqualByComparingTo("2");
        }
    }
    @Test void frozenTimezoneStillUsesSingleAcceptanceInstant() throws Exception {
        try(var b=signedIn()) {
            var g=create(b);String p=path(g);
            b.request("PUT",p+"/weekly-plans/2026-11-09",Map.of("plannedUnits",2),true);
            b.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);
            crossesBoundary(cutoff.minusMillis(1),cutoff);
            var response=b.request("POST",p+"/progress",entry(UUID.randomUUID(),2,"2026-11-08"),true);
            assertThat(response.statusCode()).isEqualTo(201);
            var saved=mapper.readTree(response.body());
            assertThat(saved.get("reportingWeek").get("timezone").asString()).isEqualTo("Asia/Almaty");
            assertThat(saved.get("createdAt").asString()).isEqualTo(cutoff.minusMillis(1).toString());
        }
    }
    @Test void getUsesConsistentSnapshotDuringConcurrentCommit() throws Exception { consistentSnapshot(false); }
    @Test void listUsesConsistentSnapshotDuringConcurrentCommit() throws Exception { consistentSnapshot(true); }
    void consistentSnapshot(boolean list) throws Exception {
        try(var b=signedIn();var executor=Executors.newSingleThreadExecutor()) {
            var g=create(b);String p=path(g);UUID id=UUID.fromString(g.get("id").asString());
            b.request("PUT",p+"/weekly-plans/2026-11-02",Map.of("plannedUnits",10),true);
            var lifetimeRead=new CountDownLatch(1);var writerCommitted=new CountDownLatch(1);
            doAnswer(call -> {
                lifetimeRead.countDown();
                if(!writerCommitted.await(10,TimeUnit.SECONDS)) throw new AssertionError("Writer did not commit");
                // Spring Data spies delegate to a repository proxy; the interface method is abstract.
                return mockingDetails(planRepository).getMockCreationSettings().getDefaultAnswer().answer(call);
            }).when(planRepository).findByGoalId(id);
            try {
                var reader=executor.submit(() -> get(b,list ? "/api/v1/goals" : p));
                assertThat(lifetimeRead.await(10,TimeUnit.SECONDS)).isTrue();
                assertThat(b.request("POST",p+"/progress",entry(UUID.randomUUID(),12,"2026-11-02"),true).statusCode()).isEqualTo(201);
                writerCommitted.countDown();
                var result=reader.get(10,TimeUnit.SECONDS);
                if(list) result=result.get("items").get(0);
                assertThat(result.get("lifetimeUnits").decimalValue()).isEqualByComparingTo("0");
                assertThat(result.get("weeklyPlans").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("0");
            } finally { writerCommitted.countDown(); reset(planRepository); }
            var fresh=get(b,p);
            assertThat(fresh.get("lifetimeUnits").decimalValue()).isEqualByComparingTo("12");
            assertThat(fresh.get("weeklyPlans").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("12");
            var saved=get(b,p+"/progress").get("items").get(0);
            assertThat(b.request("POST",p+"/progress/"+saved.get("id").asString()+"/void",null,true).statusCode()).isEqualTo(200);
            fresh=get(b,p);
            assertThat(fresh.get("lifetimeUnits").decimalValue()).isEqualByComparingTo("0");
            assertThat(fresh.get("weeklyPlans").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("0");
        }
    }

}
