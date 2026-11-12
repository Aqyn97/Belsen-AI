package com.belsenai.identity;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.BigDecimal;
import com.belsenai.analytics.*;
import com.belsenai.goals.*;
import com.belsenai.identity.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import org.springframework.test.util.AopTestUtils;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("local")
class DashboardIT extends PostgresHttpSupport {
    @MockitoBean Clock clock;
    @MockitoSpyBean DashboardReader reader;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportingCalendar calendar;
    @Autowired UserRepository users;
    @Autowired GoalService goals;
    @Autowired PlatformTransactionManager transactions;
    final AtomicReference<Instant> now=new AtomicReference<>();
    final Instant monday=Instant.parse("2026-11-02T09:00:00Z"),end=Instant.parse("2026-11-08T19:00:00Z");
    final String base="/api/v1/analytics/dashboard";
    @BeforeEach void time() { now.set(Instant.parse("2026-11-04T12:00:00Z"));when(clock.instant()).thenAnswer(call -> now.get()); }
    record Account(Browser browser,UUID id) implements AutoCloseable { public void close() { browser.close(); } }
    Account account() throws Exception {
        Browser b=browser();b.csrf();var r=b.request("POST","/api/v1/auth/register",registration(email()),true);
        assertThat(r.statusCode()).isEqualTo(201);b.csrf();return new Account(b,UUID.fromString(mapper.readTree(r.body()).get("id").asString()));
    }
    Timestamp ts(Instant at) { return Timestamp.from(at); }
    UUID week(UUID owner) {
        Instant current=now.get();now.set(monday.minusSeconds(3600));calendar.beginTracking(owner);now.set(current);
        return jdbc.queryForObject("select id from reporting_weeks where owner_id=? and week_start='2026-11-02'",UUID.class,owner);
    }
    JsonNode dashboard(Account a) throws Exception { return dashboard(a,""); }
    JsonNode dashboard(Account a,String query) throws Exception {
        var r=a.browser.request("GET",base+query,null,false);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(r.body());
    }
    UUID task(UUID owner,String due,Instant assigned) {
        UUID w=week(owner),id=UUID.randomUUID();
        jdbc.update("insert into tasks(id,owner_id,title,original_due_date,current_due_date,original_reporting_week_id,status,created_at,updated_at,event_sequence,version) values(?,?,'Task',cast(? as date),cast(? as date),?,'OPEN',?,?,2,0)",id,owner,due,due,w,ts(monday),ts(assigned));
        event(owner,id,1,monday,"CREATED","OPEN",null,false);
        event(owner,id,2,assigned,"DUE_ASSIGNED","OPEN",due,false);return id;
    }
    void event(UUID owner,UUID task,long sequence,Instant at,String type,String status,String due,boolean archived) {
        jdbc.update("""
            insert into task_events(id,task_id,owner_id,sequence,type,occurred_at,reporting_week_id,original_reporting_week_id,original_due_date,current_due_date,status,completed_at,archived_at)
            select ?,id,owner_id,?,?,?,original_reporting_week_id,
              case when ?='CREATED' then null else original_reporting_week_id end,
              case when ?='CREATED' then null else original_due_date end,cast(? as date),?,cast(? as timestamptz),cast(? as timestamptz)
            from tasks where id=? and owner_id=?
            """,UUID.randomUUID(),sequence,type,ts(at),type,type,due,status,status.equals("COMPLETED") ? ts(at) : null,archived ? ts(at) : null,task,owner);
    }
    UUID habit(UUID owner,int mask) {
        UUID w=week(owner),h=UUID.randomUUID();
        jdbc.update("insert into habits(id,owner_id,title,created_at,updated_at,version,revision_sequence) values(?,?,'Read',?,?,0,1)",h,owner,ts(monday),ts(monday));
        schedule(owner,h,1,mask,"2026-11-02",monday,w,true);return h;
    }
    void schedule(UUID owner,UUID h,long seq,int mask,String effective,Instant at,UUID w,boolean enabled) {
        jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?, ?,cast(? as date),'2026-11-02',?,?,?,?)",UUID.randomUUID(),h,owner,seq,effective,mask,enabled,ts(at),w);
    }
    void checkin(UUID owner,UUID h,String date,long seq,Instant at,String action) {
        UUID w=week(owner);
        var existing=jdbc.queryForList("select id from habit_checkins where habit_id=? and local_date=cast(? as date)",UUID.class,h,date);
        UUID c;
        if(existing.isEmpty()) {
            c=UUID.randomUUID();UUID s=jdbc.queryForObject("select id from habit_schedule_versions where habit_id=? order by sequence limit 1",UUID.class,h);
            jdbc.update("insert into habit_checkins(id,habit_id,owner_id,local_date,reporting_week_id,schedule_version_id,completed,rest_planned,version,event_sequence,created_at,updated_at) values(?,?,?,cast(? as date),?,?,false,false,0,1,?,?)",c,h,owner,date,w,s,ts(at),ts(at));
        } else c=existing.getFirst();
        boolean complete=action.equals("COMPLETED"),rest=action.equals("REST_PLANNED");
        jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reason,reporting_week_id) values(?,?,?,?,?,?,?,?,?, ?,?)",UUID.randomUUID(),c,h,owner,seq,action,ts(at),complete,rest,rest ? "Travel" : null,w);
    }
    UUID goal(UUID owner) {
        UUID id=UUID.randomUUID();jdbc.update("insert into goals(id,owner_id,title,target,unit,created_at,updated_at,version) values(?,?,'Current title',20,'pages',?,?,0)",id,owner,ts(monday),ts(monday));return id;
    }
    void plan(UUID owner,UUID goal,Object units,Instant created) {
        UUID w=week(owner);jdbc.update("insert into goal_weekly_plans(id,goal_id,owner_id,reporting_week_id,planned_units,created_at,updated_at,version) values(?,?,?,?,cast(? as numeric),?,?,0)",UUID.randomUUID(),goal,owner,w,units,ts(created),ts(created));
    }
    void progress(UUID owner,UUID goal,Object quantity,Instant created,Instant voided) {
        UUID w=week(owner);jdbc.update("insert into goal_progress_entries(id,goal_id,owner_id,reporting_week_id,submission_id,quantity,effective_date,created_at,voided_at) values(?,?,?,?,?,cast(? as numeric),'2026-11-02',?,cast(? as timestamptz))",UUID.randomUUID(),goal,owner,w,UUID.randomUUID(),quantity,ts(created),voided==null ? null : ts(voided));
    }
    @Test void emptyAccountHasHonestCurrentWeekPreviewAndNoWrites() throws Exception {
        try(var a=account()) {
            var result=dashboard(a);assertThat(result.get("period").get("calendarStatus").asString()).isEqualTo("UNFROZEN");
            assertThat(result.get("period").get("reportStatus").asString()).isEqualTo("PROVISIONAL");
            assertThat(result.get("tasks").get("percentage").isNull()).isTrue();assertThat(result.get("tasks").get("reason").asString()).isEqualTo("NO_SCHEDULED_TASKS");
            assertThat(result.get("habits").get("reason").asString()).isEqualTo("NO_SCHEDULED_ACTIVITY");
            assertThat(result.get("goals").get("items").size()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from reporting_weeks where owner_id=?",Integer.class,a.id)).isZero();
        }
    }
    @Test void authenticationExactRouteAndCacheHeadersRemainProtected() throws Exception {
        try(var b=browser();var a=account()) {
            var r=b.request("GET",base,null,false);assertThat(r.statusCode()).isEqualTo(401);assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");assertThat(r.body()).doesNotContain("SQL","password_hash");
            assertThat(a.browser.request("POST",base,Map.of(),false).statusCode()).isEqualTo(403);
            assertThat(a.browser.request("POST",base,Map.of(),true).statusCode()).isEqualTo(403);
            assertThat(a.browser.request("GET",base+"/other",null,false).statusCode()).isEqualTo(403);
            dashboard(a);
        }
    }
    @Test void invalidUnknownAndRepeatedParametersReturnSafeProblems() throws Exception {
        try(var a=account()) {
            for(String query:List.of("?weekStart=2026-11-03","?cutoff=2026-11-03T00:00:00Z","?weekStart=bad","?cutoff=bad","?goalsPage=-1","?goalsSize=0","?goalsSize=101","?ownerId="+UUID.randomUUID(),"?timezone=UTC","?goalsPage=0&goalsPage=1","?weekStart=","?weekStart=+999999999-01-01")) {
                var r=a.browser.request("GET",base+query,null,false);assertThat(r.statusCode()).withFailMessage(query+": "+r.body()).isEqualTo(400);
                assertThat(r.body()).doesNotContain("SELECT","stackTrace");
            }
        }
    }
    @Test void periodAndCutoffBoundsAreEnforced() throws Exception {
        try(var a=account()) {
            week(a.id);
            for(String cutoff:List.of("2026-11-01T19:00:00Z","2026-11-01T18:59:59Z","2026-11-09T00:00:00Z","2026-11-05T00:00:00Z"))
                assertThat(a.browser.request("GET",base+"?weekStart=2026-11-02&cutoff="+cutoff,null,false).statusCode()).isEqualTo(400);
            assertThat(a.browser.request("GET",base+"?weekStart=2026-11-09",null,false).statusCode()).isEqualTo(400);
        }
    }
    @Test void tasksCountOriginalCohortDespiteRescheduleRemovalAndArchive() throws Exception {
        try(var a=account()) {
            var one=task(a.id,"2026-11-02",monday);event(a.id,one,3,monday.plusSeconds(1),"COMPLETED","COMPLETED","2026-11-02",false);
            var two=task(a.id,"2026-11-03",monday);event(a.id,two,3,monday.plusSeconds(2),"RESCHEDULED","OPEN","2026-12-01",false);
            var three=task(a.id,"2026-11-04",monday);event(a.id,three,3,monday.plusSeconds(3),"RESCHEDULED","OPEN",null,false);event(a.id,three,4,monday.plusSeconds(4),"ARCHIVED","OPEN",null,true);
            var result=dashboard(a).get("tasks");assertThat(result.get("scheduled").asLong()).isEqualTo(3);assertThat(result.get("completed").asLong()).isEqualTo(1);assertThat(result.get("incomplete").asLong()).isEqualTo(2);assertThat(result.get("percentage").decimalValue()).isEqualByComparingTo("33.3");
        }
    }
    @Test void firstDueAssignmentAfterCutoffIsNotRetroactive() throws Exception {
        try(var a=account()) {
            task(a.id,"2026-11-02",monday.plusSeconds(2));
            var result=dashboard(a,"?weekStart=2026-11-02&cutoff="+monday.plusSeconds(1));
            assertThat(result.get("tasks").get("scheduled").asLong()).isZero();
        }
    }
    @Test void futureDueDaysCountButEqualCutoffEventsDoNot() throws Exception {
        try(var a=account()) {
            UUID t=task(a.id,"2026-11-08",monday);event(a.id,t,3,monday.plusSeconds(1),"COMPLETED","COMPLETED","2026-11-08",false);
            var at=dashboard(a,"?weekStart=2026-11-02&cutoff="+monday.plusSeconds(1)).get("tasks");
            assertThat(at.get("scheduled").asLong()).isEqualTo(1);assertThat(at.get("completed").asLong()).isZero();
            var after=dashboard(a,"?weekStart=2026-11-02&cutoff="+monday.plusSeconds(1).plusNanos(1)).get("tasks");assertThat(after.get("completed").asLong()).isEqualTo(1);
        }
    }
    @Test void reopeningUsesSequenceWhenEventTimesAreEqual() throws Exception {
        try(var a=account()) {
            UUID t=task(a.id,"2026-11-02",monday);Instant at=monday.plusSeconds(1);
            event(a.id,t,3,at,"COMPLETED","COMPLETED","2026-11-02",false);event(a.id,t,4,at,"REOPENED","OPEN","2026-11-02",false);
            assertThat(dashboard(a).get("tasks").get("completed").asLong()).isZero();
        }
    }
    @Test void lateReopenDoesNotChangeEarlierCompletedResult() throws Exception {
        try(var a=account()) {
            UUID t=task(a.id,"2026-11-02",monday);event(a.id,t,3,monday.plusSeconds(1),"COMPLETED","COMPLETED","2026-11-02",false);
            event(a.id,t,4,monday.plusSeconds(3),"REOPENED","OPEN","2026-11-02",false);
            assertThat(dashboard(a,"?weekStart=2026-11-02&cutoff="+monday.plusSeconds(2)).get("tasks").get("completed").asLong()).isEqualTo(1);
        }
    }
    @Test void oneIncompleteHabitMakesTheWholeDayUnsuccessful() throws Exception {
        try(var a=account()) {
            UUID first=habit(a.id,1);habit(a.id,1);checkin(a.id,first,"2026-11-02",1,monday.plusSeconds(1),"COMPLETED");
            var result=dashboard(a).get("habits");assertThat(result.get("eligibleDays").asLong()).isEqualTo(1);assertThat(result.get("successfulDays").asLong()).isZero();
            assertThat(result.get("percentage").decimalValue()).isEqualByComparingTo("0");
        }
    }
    @Test void allHabitsCompleteCountsOneDayNotTwo() throws Exception {
        try(var a=account()) {
            UUID first=habit(a.id,1),second=habit(a.id,1);for(UUID h:List.of(first,second))checkin(a.id,h,"2026-11-02",1,monday.plusSeconds(1),"COMPLETED");
            var result=dashboard(a).get("habits");assertThat(result.get("eligibleDays").asLong()).isEqualTo(1);assertThat(result.get("successfulDays").asLong()).isEqualTo(1);
        }
    }
    @Test void missingOccurrencesRemainIncompleteAndFutureDaysAreExcluded() throws Exception {
        try(var a=account()) {
            habit(a.id,127);var result=dashboard(a).get("habits");assertThat(result.get("eligibleDays").asLong()).isEqualTo(3);assertThat(result.get("successfulDays").asLong()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from habit_checkins where owner_id=?",Integer.class,a.id)).isZero();
        }
    }
    @Test void plannedRestExemptsOnlyItsHabit() throws Exception {
        try(var a=account()) {
            UUID first=habit(a.id,2),second=habit(a.id,2);checkin(a.id,first,"2026-11-03",1,monday.plusSeconds(1),"REST_PLANNED");checkin(a.id,second,"2026-11-03",1,monday.plusSeconds(1),"COMPLETED");
            var result=dashboard(a).get("habits");assertThat(result.get("eligibleDays").asLong()).isEqualTo(1);assertThat(result.get("successfulDays").asLong()).isEqualTo(1);
        }
    }
    @Test void allRestDaysProduceNoDataAndCancellationRestoresObligation() throws Exception {
        try(var a=account()) {
            UUID h=habit(a.id,2);checkin(a.id,h,"2026-11-03",1,monday.plusSeconds(1),"REST_PLANNED");
            assertThat(dashboard(a).get("habits").get("percentage").isNull()).isTrue();
            checkin(a.id,h,"2026-11-03",2,monday.plusSeconds(2),"REST_CANCELLED");
            assertThat(dashboard(a).get("habits").get("eligibleDays").asLong()).isEqualTo(1);
        }
    }
    @Test void archiveAndScheduleAcceptedLaterCannotRewriteEarlierCutoff() throws Exception {
        try(var a=account()) {
            UUID h=habit(a.id,127),w=week(a.id);Instant cutoff=monday.plusSeconds(1);
            schedule(a.id,h,2,0,"2026-11-03",monday.plusSeconds(2),w,false);
            jdbc.update("update habits set archived_at=?,archive_effective_date='2026-11-03' where id=?",ts(monday.plusSeconds(2)),h);
            assertThat(dashboard(a,"?weekStart=2026-11-02&cutoff="+cutoff).get("habits").get("eligibleDays").asLong()).isEqualTo(1);
            assertThat(dashboard(a).get("habits").get("eligibleDays").asLong()).isEqualTo(1);
        }
    }
    @Test void voidBeforeAtAndAfterCutoffUsesHistoricalGoalUnits() throws Exception {
        try(var a=account()) {
            UUID g=goal(a.id);Instant cutoff=monday.plusSeconds(10);plan(a.id,g,"10.0000",monday);
            progress(a.id,g,"1.0001",monday.plusSeconds(1),cutoff.minusSeconds(1));progress(a.id,g,"2.0002",monday.plusSeconds(1),cutoff);progress(a.id,g,"3.0003",monday.plusSeconds(1),cutoff.plusSeconds(1));
            progress(a.id,g,"9.0000",cutoff,null);
            var row=dashboard(a,"?weekStart=2026-11-02&cutoff="+cutoff).get("goals").get("items").get(0);
            assertThat(row.get("completedUnits").decimalValue()).isEqualByComparingTo("5.0005");assertThat(row.get("plannedUnits").decimalValue()).isEqualByComparingTo("10");
            assertThat(row.get("percentage").decimalValue()).isEqualByComparingTo("50.0");assertThat(row.get("titleMetadata").asString()).isEqualTo("CURRENT");
        }
    }
    @Test void goalWithoutTargetStillReportsExplicitUnits() throws Exception {
        try(var a=account()) {
            UUID g=goal(a.id);progress(a.id,g,"3.1250",monday.plusSeconds(1),null);
            var row=dashboard(a).get("goals").get("items").get(0);assertThat(row.get("completedUnits").decimalValue()).isEqualByComparingTo("3.125");
            assertThat(row.get("plannedUnits").isNull()).isTrue();assertThat(row.get("percentage").isNull()).isTrue();assertThat(row.get("reason").asString()).isEqualTo("NO_WEEKLY_TARGET");
        }
    }
    @Test void targetCreatedAfterCutoffIsUnavailableAndLaterGoalsAreExcluded() throws Exception {
        try(var a=account()) {
            UUID g=goal(a.id);Instant cutoff=monday.plusSeconds(2);plan(a.id,g,10,cutoff.plusSeconds(1));
            UUID later=goal(a.id);jdbc.update("update goals set created_at=?,updated_at=? where id=?",ts(cutoff.plusSeconds(1)),ts(cutoff.plusSeconds(1)),later);
            var result=dashboard(a,"?weekStart=2026-11-02&cutoff="+cutoff).get("goals");assertThat(result.get("totalElements").asLong()).isEqualTo(1);assertThat(result.get("items").get(0).get("plannedUnits").isNull()).isTrue();
        }
    }
    @Test void archivedGoalsWithPlansOrProgressRemainAndJoinsDoNotMultiplyUnits() throws Exception {
        try(var a=account()) {
            UUID g=goal(a.id);plan(a.id,g,10,monday);progress(a.id,g,3,monday.plusSeconds(1),null);progress(a.id,g,4,monday.plusSeconds(1),null);
            jdbc.update("update goals set archived_at=? where id=?",ts(monday.plusSeconds(2)),g);
            UUID empty=goal(a.id);jdbc.update("update goals set archived_at=? where id=?",ts(monday.plusSeconds(2)),empty);
            var result=dashboard(a).get("goals");assertThat(result.get("totalElements").asLong()).isEqualTo(1);assertThat(result.get("items").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("7");
        }
    }
    @Test void aggregatePrecisionExceedsEntryPrecisionWithoutOverflowOrCappingUnits() throws Exception {
        try(var a=account()) {
            UUID g=goal(a.id);plan(a.id,g,1,monday);for(int i=0;i<2;i++)progress(a.id,g,"999999999999999.9999",monday.plusSeconds(1),null);
            var row=dashboard(a).get("goals").get("items").get(0);assertThat(row.get("completedUnits").decimalValue()).isEqualByComparingTo("1999999999999999.9998");assertThat(row.get("percentage").decimalValue()).isEqualByComparingTo("100");
        }
    }
    @Test void goalPaginationIsStableAndDoesNotRestrictOtherMetrics() throws Exception {
        try(var a=account()) {
            for(int i=0;i<3;i++)goal(a.id);task(a.id,"2026-11-02",monday);
            var first=dashboard(a,"?goalsSize=1");var second=dashboard(a,"?goalsSize=1&goalsPage=1");
            assertThat(first.get("goals").get("totalElements").asLong()).isEqualTo(3);assertThat(first.get("goals").get("items").size()).isEqualTo(1);
            assertThat(first.get("goals").get("items").get(0).get("goalId")).isNotEqualTo(second.get("goals").get("items").get(0).get("goalId"));
            assertThat(second.get("tasks").get("scheduled").asLong()).isEqualTo(1);
        }
    }
    @Test void otherAccountsCannotInfluenceAnyMetric() throws Exception {
        try(var a=account();var b=account()) {
            task(a.id,"2026-11-02",monday);habit(a.id,127);UUID g=goal(a.id);progress(a.id,g,9,monday.plusSeconds(1),null);
            var result=dashboard(b);assertThat(result.get("tasks").get("scheduled").asLong()).isZero();assertThat(result.get("habits").get("eligibleDays").asLong()).isZero();assertThat(result.get("goals").get("totalElements").asLong()).isZero();
        }
    }
    @Test void closedWeeksStayExplicitlyUnfinalizedAndUseWeekEnd() throws Exception {
        try(var a=account()) {
            task(a.id,"2026-11-02",monday);now.set(end.plusSeconds(1));
            var result=dashboard(a,"?weekStart=2026-11-02").get("period");assertThat(result.get("reportStatus").asString()).isEqualTo("CLOSED_UNFINALIZED");assertThat(result.get("cutoff").asString()).isEqualTo(end.toString());
        }
    }
    @Test void missingPostBaselineWeeksAreReconstructedButPreBaselineHistoryIsUnavailable() throws Exception {
        try(var a=account()) {
            week(a.id);now.set(Instant.parse("2026-11-25T12:00:00Z"));
            var result=dashboard(a,"?weekStart=2026-11-09").get("period");assertThat(result.get("calendarStatus").asString()).isEqualTo("RECONSTRUCTED");
            assertThat(a.browser.request("GET",base+"?weekStart=2026-10-26",null,false).statusCode()).isEqualTo(404);
            assertThat(jdbc.queryForObject("select count(*) from reporting_weeks where owner_id=?",Integer.class,a.id)).isEqualTo(1);
        }
    }
    @Test void frozenTimezoneWinsAndEastwardWeekIsShortened() throws Exception {
        try(var a=account()) {
            week(a.id);assertThat(a.browser.request("PATCH","/api/v1/me",Map.of("timezone","Asia/Tokyo"),true).statusCode()).isEqualTo(200);
            assertThat(dashboard(a).get("period").get("timezone").asString()).isEqualTo("Asia/Almaty");
            now.set(end.plusSeconds(1));var next=dashboard(a).get("period");assertThat(next.get("weekStart").asString()).isEqualTo("2026-11-09");assertThat(next.get("startsAt").asString()).isEqualTo(end.toString());
            assertThat(next.get("timezone").asString()).isEqualTo("Asia/Tokyo");
        }
    }
    @Test void daylightSavingWeekUsesActualBoundaries() throws Exception {
        try(var a=account()) {
            a.browser.request("PATCH","/api/v1/me",Map.of("timezone","America/New_York"),true);
            now.set(Instant.parse("2026-10-26T12:00:00Z"));calendar.beginTracking(a.id);now.set(Instant.parse("2026-11-02T12:00:00Z"));
            var p=dashboard(a,"?weekStart=2026-10-26").get("period");assertThat(Duration.between(Instant.parse(p.get("startsAt").asString()),Instant.parse(p.get("endsAt").asString())).toHours()).isEqualTo(169);
        }
    }
    @Test void commitBetweenMetricsCannotMixDatabaseSnapshots() throws Exception {
        try(var a=account();var pool=Executors.newSingleThreadExecutor()) {
            UUID h=habit(a.id,1),g=goal(a.id);UUID w=week(a.id);DashboardReader target=AopTestUtils.getUltimateTargetObject(reader);var captured=new CountDownLatch(1);var committed=new CountDownLatch(1);
            doAnswer(call -> {Object result=call.callRealMethod();captured.countDown();if(!committed.await(10,TimeUnit.SECONDS))throw new AssertionError("Writer did not commit");return result;}).when(target).tasks(eq(a.id),any(),any());
            try {
                var request=pool.submit(() -> dashboard(a));assertThat(captured.await(10,TimeUnit.SECONDS)).isTrue();
                // Commit data timestamped before the captured cutoff, to detect isolation rather than merely filtering.
                progress(a.id,g,5,monday.plusSeconds(1),null);checkin(a.id,h,"2026-11-02",1,monday.plusSeconds(1),"COMPLETED");
                committed.countDown();var result=request.get(15,TimeUnit.SECONDS);
                assertThat(result.get("habits").get("successfulDays").asLong()).isZero();assertThat(result.get("goals").get("items").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("0");
            } finally { committed.countDown();reset(target); }
            assertThat(dashboard(a).get("habits").get("successfulDays").asLong()).isEqualTo(1);
        }
    }
    @Test void lateCommitCanChangeClosedUnfinalizedResultWithoutClaimingFinality() throws Exception {
        try(var a=account();var pool=Executors.newSingleThreadExecutor()) {
            UUID g=goal(a.id);week(a.id);var accepted=new CountDownLatch(1);var commit=new CountDownLatch(1);
            now.set(end.minusSeconds(1));
            var writer=pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                goals.record(a.id,g,new GoalDtos.Progress(UUID.randomUUID(),BigDecimal.ONE,LocalDate.parse("2026-11-08")));
                accepted.countDown();try {if(!commit.await(10,TimeUnit.SECONDS))throw new AssertionError("Reader did not finish");} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);}return true;
            }));
            try {
                assertThat(accepted.await(10,TimeUnit.SECONDS)).isTrue();now.set(end.plusSeconds(1));
                var before=dashboard(a,"?weekStart=2026-11-02");assertThat(before.get("goals").get("items").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("0");
                assertThat(before.get("period").get("reportStatus").asString()).isEqualTo("CLOSED_UNFINALIZED");
            } finally {commit.countDown();}
            assertThat(writer.get(15,TimeUnit.SECONDS)).isTrue();
            var after=dashboard(a,"?weekStart=2026-11-02");assertThat(after.get("goals").get("items").get(0).get("completedUnits").decimalValue()).isEqualByComparingTo("1");
        }
    }
    @Test void archivedCompletedTasksRemainCompleteInOriginalCohort() throws Exception {
        try(var a=account()) {
            UUID t=task(a.id,"2026-11-02",monday);event(a.id,t,3,monday.plusSeconds(1),"COMPLETED","COMPLETED","2026-11-02",false);
            event(a.id,t,4,monday.plusSeconds(2),"ARCHIVED","COMPLETED","2026-11-02",true);
            var result=dashboard(a).get("tasks");assertThat(result.get("scheduled").asLong()).isEqualTo(1);assertThat(result.get("completed").asLong()).isEqualTo(1);
        }
    }
    @Test void taskStateCanComeFromAnEarlierActionWeek() throws Exception {
        try(var a=account()) {
            UUID t=task(a.id,"2026-11-02",monday);UUID previous=UUID.randomUUID();
            jdbc.update("insert into reporting_weeks(id,owner_id,week_start,timezone,starts_at,ends_at) values(?,?,'2026-10-26','Asia/Almaty','2026-10-25T19:00:00Z','2026-11-01T19:00:00Z')",previous,a.id);
            // A task scheduled for this week was already completed in the preceding week.
            jdbc.update("insert into task_events(id,task_id,owner_id,sequence,type,occurred_at,reporting_week_id,original_reporting_week_id,original_due_date,current_due_date,status,completed_at) select ?,id,owner_id,3,'COMPLETED','2026-11-01T18:00:00Z',?,original_reporting_week_id,original_due_date,current_due_date,'COMPLETED','2026-11-01T18:00:00Z' from tasks where id=?",UUID.randomUUID(),previous,t);
            assertThat(dashboard(a).get("tasks").get("completed").asLong()).isEqualTo(1);
        }
    }
    @Test void acceptanceTimeIsFilteredBeforeSelectingHabitRevision() throws Exception {
        try(var a=account()) {
            UUID h=habit(a.id,127),w=week(a.id);Instant cutoff=monday.plusSeconds(1);
            // Persistence-level fixture isolates recordedAt independently of effectiveFrom.
            schedule(a.id,h,2,0,"2026-11-02",monday.plusSeconds(2),w,false);
            assertThat(dashboard(a,"?weekStart=2026-11-02&cutoff="+cutoff).get("habits").get("eligibleDays").asLong()).isEqualTo(1);
            assertThat(dashboard(a).get("habits").get("eligibleDays").asLong()).isZero();
        }
    }
    @Test void restAndUndoStatesUseTheirLastSequenceBeforeCutoff() throws Exception {
        try(var a=account()) {
            UUID h=habit(a.id,1);checkin(a.id,h,"2026-11-02",1,monday.plusSeconds(1),"COMPLETED");
            checkin(a.id,h,"2026-11-02",2,monday.plusSeconds(3),"UNDONE");
            assertThat(dashboard(a,"?weekStart=2026-11-02&cutoff="+monday.plusSeconds(2)).get("habits").get("successfulDays").asLong()).isEqualTo(1);
            assertThat(dashboard(a).get("habits").get("successfulDays").asLong()).isZero();
        }
    }
    @Test void westwardGapReturnsConflictInsteadOfGuessingAnotherTimezone() throws Exception {
        try(var a=account()) {
            week(a.id);a.browser.request("PATCH","/api/v1/me",Map.of("timezone","America/Los_Angeles"),true);now.set(end.plusSeconds(1));
            assertThat(a.browser.request("GET",base,null,false).statusCode()).isEqualTo(409);
            assertThat(dashboard(a,"?weekStart=2026-11-02").get("period").get("timezone").asString()).isEqualTo("Asia/Almaty");
        }
    }
    @Test void springDstWeekAndSkippedDayUseZoneIntervals() throws Exception {
        try(var a=account()) {
            a.browser.request("PATCH","/api/v1/me",Map.of("timezone","America/New_York"),true);
            now.set(Instant.parse("2026-03-02T12:00:00Z"));calendar.beginTracking(a.id);now.set(Instant.parse("2026-03-09T12:00:00Z"));
            var p=dashboard(a,"?weekStart=2026-03-02").get("period");assertThat(Duration.between(Instant.parse(p.get("startsAt").asString()),Instant.parse(p.get("endsAt").asString())).toHours()).isEqualTo(167);
        }
        try(var a=account()) {
            a.browser.request("PATCH","/api/v1/me",Map.of("timezone","Pacific/Apia"),true);
            now.set(Instant.parse("2011-12-26T12:00:00Z"));calendar.beginTracking(a.id);now.set(Instant.parse("2011-12-31T12:00:00Z"));
            UUID w=jdbc.queryForObject("select id from reporting_weeks where owner_id=?",UUID.class,a.id),h=UUID.randomUUID();
            jdbc.update("insert into habits(id,owner_id,title,created_at,updated_at,version,revision_sequence) values(?,?,'Skipped Friday','2011-12-26T13:00:00Z','2011-12-26T13:00:00Z',0,1)",h,a.id);
            jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,1,'2011-12-26','2011-12-26',16,true,'2011-12-26T13:00:00Z',?)",UUID.randomUUID(),h,a.id,w);
            var result=dashboard(a,"?weekStart=2011-12-26");assertThat(result.get("period").get("timezone").asString()).isEqualTo("Pacific/Apia");
            assertThat(result.get("habits").get("eligibleDays").asLong()).isZero();
        }
    }
    @Test void noHistoricalPreviewIsInventedForAnEmptyAccount() throws Exception {
        try(var a=account()) {
            assertThat(a.browser.request("GET",base+"?weekStart=2026-10-26",null,false).statusCode()).isEqualTo(404);
            assertThat(a.browser.request("GET",base+"?weekStart=2026-11-09",null,false).statusCode()).isEqualTo(400);
        }
    }
    @Test void jdbcQueriesReallyUseOneReadOnlyRepeatableReadTransaction() throws Exception {
        try(var a=account()) {
            DashboardReader target=AopTestUtils.getUltimateTargetObject(reader);
            doAnswer(call -> {
                assertThat(jdbc.queryForObject("show transaction_isolation",String.class)).isEqualTo("repeatable read");
                assertThat(jdbc.queryForObject("show transaction_read_only",String.class)).isEqualTo("on");
                return call.callRealMethod();
            }).when(target).tasks(eq(a.id),any(),any());
            try {dashboard(a);} finally {reset(target);}
        }
    }
    @Test void queryCountStaysConstantAsAccountDataGrowsAndRealTaskPlanIsReviewed() throws Exception {
        try(var a=account();var b=account()) {
            UUID first=task(a.id,"2026-11-02",monday),foreign=task(b.id,"2026-11-02",monday);
            UUID h=habit(a.id,127);goal(a.id);
            NamedParameterJdbcTemplate target=AopTestUtils.getUltimateTargetObject(named);AtomicInteger queries=new AtomicInteger();AtomicBoolean explain=new AtomicBoolean();
            AtomicReference<List<Map<String,Object>>> plan=new AtomicReference<>();
            doAnswer(call -> {
                queries.incrementAndGet();String sql=call.getArgument(0);SqlParameterSource p=call.getArgument(1);
                if(sql.contains("FROM tasks t") && !sql.startsWith("EXPLAIN") && explain.compareAndSet(true,false)) plan.set(target.queryForList("EXPLAIN (ANALYZE, BUFFERS) "+sql,p));
                return call.callRealMethod();
            }).when(target).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
            doAnswer(call -> {
                queries.incrementAndGet();String sql=call.getArgument(0);SqlParameterSource p=call.getArgument(1);
                if(sql.contains("FROM tasks t") && explain.compareAndSet(true,false)) plan.set(target.queryForList("EXPLAIN (ANALYZE, BUFFERS) "+sql,p));
                return call.callRealMethod();
            }).when(target).queryForObject(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
            try {
                dashboard(a);int small=queries.get();assertThat(small).isGreaterThan(0);queries.set(0);
                for(UUID template:List.of(first,foreign)) {
                    jdbc.update("insert into tasks select md5(t.id::text||n::text)::uuid,t.owner_id,t.goal_id,t.title,t.description,t.original_due_date,t.current_due_date,t.original_reporting_week_id,t.estimated_duration_minutes,t.status,t.completed_at,t.archived_at,t.created_at,t.updated_at,t.event_sequence,t.version from tasks t cross join generate_series(1,2000) n where t.id=?",template);
                }
                jdbc.update("insert into task_events select md5(t.id::text||e.sequence::text)::uuid,t.id,t.owner_id,e.sequence,e.type,e.occurred_at,e.reporting_week_id,e.original_reporting_week_id,e.original_due_date,e.current_due_date,e.status,e.completed_at,e.archived_at from tasks t join task_events e on e.task_id=? where t.owner_id=? and t.id<>?",first,a.id,first);
                jdbc.update("insert into habits select md5(h.id::text||n::text)::uuid,h.owner_id,h.title,h.description,h.archived_at,h.archive_effective_date,h.created_at,h.updated_at,h.version,h.revision_sequence from habits h cross join generate_series(1,1000) n where h.id=?",h);
                jdbc.update("insert into habit_schedule_versions select md5(h.id::text||'schedule')::uuid,h.id,h.owner_id,s.sequence,s.effective_from,s.start_date,s.end_date,s.weekdays_mask,s.enabled,s.recorded_at,s.reporting_week_id from habits h join habit_schedule_versions s on s.habit_id=? where h.owner_id=? and h.id<>?",h,a.id,h);
                jdbc.execute("analyze tasks");jdbc.execute("analyze task_events");jdbc.execute("analyze habits");jdbc.execute("analyze habit_schedule_versions");
                var result=dashboard(a);assertThat(queries.get()).isEqualTo(small);
                System.out.println("Dashboard native SQL calls: small="+small+", large="+queries.get());
                assertThat(result.get("tasks").get("scheduled").asLong()).isEqualTo(2001);assertThat(result.get("habits").get("eligibleDays").asLong()).isEqualTo(3);
                explain.set(true);dashboard(a);assertThat(plan.get()).isNotEmpty();
                System.out.println("Dashboard task query plan (isolated fixture): "+plan.get());
            } finally {reset(target);}
        }
    }

    @Test void invalidStoredTimezoneReturnsConflictWithoutLeakingConfiguration() throws Exception {
        try(var a=account()) {
            jdbc.update("insert into reporting_weeks(id,owner_id,week_start,timezone,starts_at,ends_at) values(?,?,'2026-11-02','Invalid/PrivateZone','2026-11-01T19:00:00Z','2026-11-08T19:00:00Z')",UUID.randomUUID(),a.id);
            var response=a.browser.request("GET",base+"?weekStart=2026-11-02",null,false);
            assertThat(response.statusCode()).isEqualTo(409);assertThat(response.body()).doesNotContain("PrivateZone","SQL","ZoneRulesException");
        }
    }
    @Test void oldFrozenWeeksWithoutPolicyDoNotAuthorizeAGuessedCurrentPreview() throws Exception {
        try(var a=account()) {
            jdbc.update("insert into reporting_weeks(id,owner_id,week_start,timezone,starts_at,ends_at) values(?,?,'2026-10-26','Asia/Almaty','2026-10-25T19:00:00Z','2026-11-01T19:00:00Z')",UUID.randomUUID(),a.id);
            assertThat(a.browser.request("GET",base,null,false).statusCode()).isEqualTo(409);
            assertThat(dashboard(a,"?weekStart=2026-10-26").get("period").get("calendarStatus").asString()).isEqualTo("FROZEN");
        }
    }

    @Test void boundedCalendarLookupMatchesExistingResolverWithFrozenNeighbors() throws Exception {
        try(var a=account()) {
            week(a.id);a.browser.request("PATCH","/api/v1/me",Map.of("timezone","Asia/Tokyo"),true);
            new TransactionTemplate(transactions).executeWithoutResult(tx -> calendar.reserve(calendar.lockOwner(a.id),LocalDate.parse("2026-11-16")));
            var expected=calendar.resolveWeek(a.id,LocalDate.parse("2026-11-09")).orElseThrow();
            var bounded=calendar.readWeek(a.id,LocalDate.parse("2026-11-09")).orElseThrow();assertThat(bounded).isEqualTo(expected);
            assertThat(bounded.startsAt()).isEqualTo(end);
        }
    }

    @Test void defaultDashboardAtExactWeekStartIsValidWithoutAnElapsedHabitDay() throws Exception {
        try(var a=account()) {
            week(a.id);new TransactionTemplate(transactions).executeWithoutResult(tx -> calendar.reserve(calendar.lockOwner(a.id),LocalDate.parse("2026-11-09")));
            now.set(end);var result=dashboard(a);
            assertThat(result.get("period").get("weekStart").asString()).isEqualTo("2026-11-09");
            assertThat(result.get("period").get("cutoff").asString()).isEqualTo(end.toString());
            assertThat(result.get("habits").get("eligibleDays").asLong()).isZero();
        }
    }

}
