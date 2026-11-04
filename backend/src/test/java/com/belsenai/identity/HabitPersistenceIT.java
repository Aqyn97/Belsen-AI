package com.belsenai.identity;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.belsenai.goals.*;
import com.belsenai.habits.*;
import com.belsenai.identity.dto.ProfileRequest;
import com.belsenai.identity.service.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("local")
class HabitPersistenceIT extends PostgresHttpSupport {
    @MockitoBean Clock clock;
    @Autowired ReportingCalendar calendar;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired HabitRepository habits;
    @Autowired HabitScheduleVersionRepository schedules;
    @Autowired HabitCheckinRepository checkins;
    @Autowired HabitCheckinEventRepository events;
    final AtomicReference<Instant> now=new AtomicReference<>();
    @BeforeEach void time() {
        now.set(Instant.parse("2026-11-02T12:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
    }
    UUID user(String zone) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into users(id,email,display_name,timezone,created_at,updated_at) values(?,?,'Habit test',?,now(),now())",id,id+"@example.test",zone);
        return id;
    }
    ReportingCalendar.WeekWindow reserve(UUID owner,String monday) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> calendar.reserve(calendar.lockOwner(owner),LocalDate.parse(monday)));
        return calendar.resolveWeek(owner,LocalDate.parse(monday)).orElseThrow();
    }
    void zone(UUID owner,String zone) { accounts.update(owner,new ProfileRequest(null,zone)); }
    long weekCount(UUID owner) { return jdbc.queryForObject("select count(*) from reporting_weeks where owner_id=?",Long.class,owner); }
    List<Map<String,Object>> policies(UUID owner) {
        return jdbc.queryForList("select * from reporting_timezone_revisions where owner_id=? order by sequence",owner);
    }
    record Fixture(UUID owner,UUID habit,UUID schedule,UUID checkin,UUID week) { }
    Fixture fixture() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);
        UUID week=reserve(owner,"2026-11-02").id(),habit=UUID.randomUUID(),schedule=UUID.randomUUID(),checkin=UUID.randomUUID();
        jdbc.update("insert into habits(id,owner_id,title,created_at,updated_at,version,revision_sequence) values(?,?,'Read',now(),now(),0,1)",habit,owner);
        jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,1,'2026-11-02','2026-11-02',127,true,now(),?)",schedule,habit,owner,week);
        jdbc.update("insert into habit_checkins(id,habit_id,owner_id,local_date,reporting_week_id,schedule_version_id,completed,rest_planned,version,event_sequence,created_at,updated_at) values(?,?,?,'2026-11-02',?,?,false,false,0,1,now(),now())",checkin,habit,owner,week,schedule);
        return new Fixture(owner,habit,schedule,checkin,week);
    }
    UUID event(Fixture f) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reporting_week_id) values(?,?,?,?,1,'COMPLETED',now(),true,false,?)",id,f.checkin,f.habit,f.owner,f.week);
        return id;
    }
    @Test void mappingsReadAllFiveTablesWithOwnedQueries() {
        var f=fixture();UUID event=event(f);
        assertThat(habits.findByIdAndOwnerId(f.habit,f.owner)).isPresent();
        assertThat(schedules.findByIdAndOwnerId(f.schedule,f.owner)).isPresent();
        assertThat(checkins.findByIdAndOwnerId(f.checkin,f.owner)).isPresent();
        assertThat(events.findByIdAndOwnerId(event,f.owner)).isPresent();
        assertThat(habits.findByIdAndOwnerId(f.habit,user("UTC"))).isEmpty();
    }
    @Test void duplicateHabitDateIsRejected() {
        var f=fixture();
        assertThatThrownBy(() -> jdbc.update("insert into habit_checkins select ?,habit_id,owner_id,local_date,reporting_week_id,schedule_version_id,completed,rest_planned,version,event_sequence,created_at,updated_at from habit_checkins where id=?",UUID.randomUUID(),f.checkin)).isInstanceOf(DataAccessException.class);
    }
    @Test void compositeOwnershipAndSameHabitScheduleAreEnforced() {
        var a=fixture();var b=fixture();
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set owner_id=? where id=?",b.owner,a.checkin)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set schedule_version_id=? where id=?",b.schedule,a.checkin)).isInstanceOf(DataAccessException.class);
        // Same owner, different habit must also fail.
        UUID other=UUID.randomUUID();
        jdbc.update("insert into habits(id,owner_id,title,created_at,updated_at,version,revision_sequence) values(?,?,'Walk',now(),now(),0,1)",other,a.owner);
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set habit_id=? where id=?",other,a.checkin)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set reporting_week_id=? where id=?",b.week,a.checkin)).isInstanceOf(DataAccessException.class);
    }
    @Test void schedulesEventsPoliciesAndWeeksRejectUpdateAndDelete() {
        var f=fixture();UUID event=event(f);
        for(var pair:List.of(Map.entry("habit_schedule_versions",f.schedule),Map.entry("habit_checkin_events",event),
                Map.entry("reporting_timezone_revisions",(UUID)policies(f.owner).getFirst().get("id")),Map.entry("reporting_weeks",f.week))) {
            assertThatThrownBy(() -> jdbc.update("update "+pair.getKey()+" set id=id where id=?",pair.getValue())).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("delete from "+pair.getKey()+" where id=?",pair.getValue())).isInstanceOf(DataAccessException.class);
        }
    }
    @Test void weekdayMasksRangesAndSequencesAreChecked() {
        var f=fixture();
        for(int mask:new int[]{-1,0,128}) {
            assertThatThrownBy(() -> jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,2,'2026-11-03','2026-11-02',?,true,now(),?)",UUID.randomUUID(),f.habit,f.owner,mask,f.week)).isInstanceOf(DataAccessException.class);
        }
        for(int mask:new int[]{1,2,4,8,16,32,64,127}) {
            jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,?,'2026-11-03','2026-11-02',?,true,now(),?)",UUID.randomUUID(),f.habit,f.owner,mask+2,mask,f.week);
        }
        assertThatThrownBy(() -> jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,end_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,200,'2026-11-03','2026-11-02','2026-11-01',1,true,now(),?)",UUID.randomUUID(),f.habit,f.owner,f.week)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set event_sequence=0 where id=?",f.checkin)).isInstanceOf(DataAccessException.class);
    }
    @Test void completionRestAndEventStateConstraintsAreChecked() {
        var f=fixture();
        assertThatThrownBy(() -> jdbc.update("update habit_checkins set completed=true,rest_planned=true where id=?",f.checkin)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reporting_week_id) values(?,?,?,?,0,'COMPLETED',now(),true,false,?)",UUID.randomUUID(),f.checkin,f.habit,f.owner,f.week)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reporting_week_id) values(?,?,?,?,1,'REST_PLANNED',now(),false,true,?)",UUID.randomUUID(),f.checkin,f.habit,f.owner,f.week)).isInstanceOf(DataAccessException.class);
        event(f);
        assertThatThrownBy(() -> event(f)).isInstanceOf(DataAccessException.class);
    }
    @Test void baselineIsIdempotentAndDoesNotInventEarlierHistory() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);calendar.beginTracking(owner);
        assertThat(policies(owner)).hasSize(1);
        assertThat(policies(owner).getFirst().get("timezone")).isEqualTo("Asia/Almaty");
        assertThat(calendar.resolveWeek(owner,LocalDate.parse("2026-10-26"))).isEmpty();
        assertThat(weekCount(owner)).isEqualTo(1);
    }
    @Test void untrackedAccountsKeepLegacyProfileTimezoneBehavior() {
        UUID owner=user("Asia/Almaty");zone(owner,"America/Los_Angeles");
        assertThat(policies(owner)).isEmpty();
        assertThat(reserve(owner,"2026-11-09").timezone()).isEqualTo("America/Los_Angeles");
    }
    @Test void missingWeeksUseProspectivePolicyNotLatestProfile() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);zone(owner,"America/Los_Angeles");
        now.set(Instant.parse("2026-11-23T12:00:00Z"));zone(owner,"Asia/Tokyo");
        long count=weekCount(owner);
        var read=new TransactionTemplate(transactions);read.setReadOnly(true);
        var past=read.execute(tx -> calendar.resolveWeek(owner,LocalDate.parse("2026-11-09")).orElseThrow());
        assertThat(past.timezone()).isEqualTo("America/Los_Angeles");assertThat(past.id()).isNull();
        assertThat(calendar.resolveWeek(owner,LocalDate.parse("2026-11-30")).orElseThrow().timezone()).isEqualTo("Asia/Tokyo");
        assertThat(weekCount(owner)).isEqualTo(count);
        assertThat(reserve(owner,"2026-11-09").startsAt()).isEqualTo(past.startsAt());
    }
    @Test void conflictingFutureReservationsRejectAndRollbackProfileChange() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);var frozen=reserve(owner,"2026-11-16");
        assertThatThrownBy(() -> zone(owner,"UTC")).hasMessageContaining("frozen future");
        assertThat(accounts.profile(owner).timezone()).isEqualTo("Asia/Almaty");
        assertThat(policies(owner)).hasSize(1);
        assertThat(calendar.resolveWeek(owner,frozen.weekStart()).orElseThrow()).isEqualTo(frozen);
    }
    @Test void matchingFutureReservationsRemainUnchanged() {
        UUID owner=user("Asia/Almaty");var future=reserve(owner,"2026-11-09");
        zone(owner,"UTC");calendar.beginTracking(owner);zone(owner,"Asia/Almaty");
        assertThat(calendar.resolveWeek(owner,future.weekStart()).orElseThrow()).isEqualTo(future);
    }
    @Test void mondayBoundaryChangesAreProspectiveAndDoNotMovePriorWeeks() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);var first=calendar.resolveWeek(owner,LocalDate.parse("2026-11-02")).orElseThrow();
        now.set(first.endsAt().minusNanos(1000));zone(owner,"Asia/Tokyo");
        var next=calendar.resolveWeek(owner,LocalDate.parse("2026-11-09")).orElseThrow();
        assertThat(next.startsAt()).isEqualTo(first.endsAt()); // Monday's beginning is shortened.
        assertThat(calendar.resolveWeek(owner,first.weekStart()).orElseThrow()).isEqualTo(first);
        now.set(first.endsAt());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var current=calendar.current(calendar.lockOwner(owner),now.get());
            assertThat(current.getWeekStart()).isEqualTo(LocalDate.parse("2026-11-09"));
        });
        zone(owner,"UTC");
        assertThat(policies(owner).getLast().get("effective_week_start").toString()).isEqualTo("2026-11-16");
    }
    @Test void dstForwardAndBackwardDaysUseZoneRules() {
        now.set(Instant.parse("2026-03-02T12:00:00Z"));UUID owner=user("America/New_York");calendar.beginTracking(owner);
        var spring=calendar.resolveDay(owner,LocalDate.parse("2026-03-08")).orElseThrow();
        var fall=calendar.resolveDay(owner,LocalDate.parse("2026-11-01")).orElseThrow();
        assertThat(Duration.between(spring.startsAt(),spring.endsAt()).toHours()).isEqualTo(23);
        assertThat(Duration.between(fall.startsAt(),fall.endsAt()).toHours()).isEqualTo(25);
        assertThat(calendar.resolveDay(owner,LocalDate.parse("2026-03-07"))).isPresent(); // Saturday.
    }
    @Test void skippedLocalDateHasNoInterval() {
        now.set(Instant.parse("2011-12-26T12:00:00Z"));UUID owner=user("Pacific/Apia");calendar.beginTracking(owner);
        assertThat(calendar.resolveDay(owner,LocalDate.parse("2011-12-30"))).isEmpty();
    }
    @Test void concurrentTimezoneUpdatesHaveUniqueOrderedRevisions() throws Exception {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            var a=executor.submit(() -> {barrier.await(5,TimeUnit.SECONDS);zone(owner,"UTC");return true;});
            var b=executor.submit(() -> {barrier.await(5,TimeUnit.SECONDS);zone(owner,"Asia/Tokyo");return true;});
            assertThat(a.get(15,TimeUnit.SECONDS)).isTrue();assertThat(b.get(15,TimeUnit.SECONDS)).isTrue();
        }
        var revisions=policies(owner);assertThat(revisions).hasSize(3);
        assertThat(revisions.stream().map(r -> ((Number)r.get("sequence")).longValue())).containsExactly(1L,2L,3L);
        assertThat(calendar.resolveWeek(owner,LocalDate.parse("2026-11-09")).orElseThrow().timezone()).isEqualTo(accounts.profile(owner).timezone());
    }
    @Test void cutoffSelectionExcludesLaterScheduleAndEventRevisions() {
        var f=fixture();Instant cutoff=Instant.parse("2030-01-01T00:00:00Z");
        jdbc.update("insert into habit_schedule_versions(id,habit_id,owner_id,sequence,effective_from,start_date,weekdays_mask,enabled,recorded_at,reporting_week_id) values(?,?,?,2,'2026-11-02','2026-11-02',1,true,?,?)",UUID.randomUUID(),f.habit,f.owner,java.sql.Timestamp.from(cutoff),f.week);
        assertThat(jdbc.queryForObject("select sequence from habit_schedule_versions where habit_id=? and effective_from<=? and recorded_at<? order by effective_from desc,sequence desc limit 1",Long.class,f.habit,java.sql.Date.valueOf("2026-11-02"),java.sql.Timestamp.from(cutoff))).isEqualTo(1);
        event(f);
        jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reporting_week_id) values(?,?,?,?,2,'UNDONE',?,false,false,?)",UUID.randomUUID(),f.checkin,f.habit,f.owner,java.sql.Timestamp.from(cutoff),f.week);
        assertThat(jdbc.queryForObject("select completed from habit_checkin_events where checkin_id=? and accepted_at<? order by sequence desc limit 1",Boolean.class,f.checkin,java.sql.Timestamp.from(cutoff))).isTrue();
    }
    @Test void failedTrackingTransactionRollsBackWeekAndBaseline() {
        UUID owner=user("UTC");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            calendar.beginTracking(owner);
            throw new IllegalStateException("synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(weekCount(owner)).isZero();assertThat(policies(owner)).isEmpty();
    }
    @Test void shortenedMondayDayWindowAndReadOnlyResolutionAgree() {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);zone(owner,"Asia/Tokyo");
        var day=calendar.resolveDay(owner,LocalDate.parse("2026-11-09")).orElseThrow();
        assertThat(Duration.between(day.startsAt(),day.endsAt()).toHours()).isEqualTo(20);
        var stored=reserve(owner,"2026-11-09");
        assertThat(stored.startsAt()).isEqualTo(day.week().startsAt());
        assertThat(calendar.resolveDay(owner,day.localDate()).orElseThrow().startsAt()).isEqualTo(day.startsAt());
    }
    @Test void concurrentReservationAndTimezoneUpdateCannotDisagree() throws Exception {
        UUID owner=user("Asia/Almaty");calendar.beginTracking(owner);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            var reservation=executor.submit(() -> {barrier.await(5,TimeUnit.SECONDS);return reserve(owner,"2026-11-09");});
            var change=executor.submit(() -> {
                barrier.await(5,TimeUnit.SECONDS);
                try { zone(owner,"UTC");return true; }
                catch(com.belsenai.common.error.ApiException conflict) { assertThat(conflict.getMessage()).contains("frozen future");return false; }
            });
            var frozen=reservation.get(15,TimeUnit.SECONDS);boolean changed=change.get(15,TimeUnit.SECONDS);
            assertThat(frozen.timezone()).isEqualTo(changed ? "UTC" : "Asia/Almaty");
            assertThat(accounts.profile(owner).timezone()).isEqualTo(frozen.timezone());
            assertThat(policies(owner)).hasSize(changed ? 2 : 1);
        }
    }
    @Test void duplicateBaselineSequenceAndCrossOwnerEventsAreRejected() {
        var f=fixture();var other=fixture();
        assertThatThrownBy(() -> jdbc.update("insert into reporting_timezone_revisions(id,owner_id,sequence,effective_week_start,timezone,recorded_at) values(?,?,1,'2026-11-02','UTC',now())",UUID.randomUUID(),f.owner)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into habit_checkin_events(id,checkin_id,habit_id,owner_id,sequence,action,accepted_at,completed,rest_planned,reporting_week_id) values(?,?,?,?,1,'COMPLETED',now(),true,false,?)",UUID.randomUUID(),f.checkin,f.habit,other.owner,other.week)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into reporting_timezone_revisions(id,owner_id,sequence,effective_week_start,timezone,recorded_at) values(?,?,2,'2026-11-03','UTC',now())",UUID.randomUUID(),f.owner)).isInstanceOf(DataAccessException.class);
    }
    @Test void trackedAccountsStillUseExistingGoalTaskAndProfileHttpFlows() throws Exception {
        try(var b=browser()) {
            b.csrf();String email=email();
            assertThat(b.request("POST","/api/v1/auth/register",registration(email),true).statusCode()).isEqualTo(201);b.csrf();
            UUID owner=jdbc.queryForObject("select id from users where email=?",UUID.class,email);
            calendar.beginTracking(owner);
            var goal=b.request("POST","/api/v1/goals",Map.of("title","Read","target",10,"unit","pages"),true);
            assertThat(goal.statusCode()).isEqualTo(201);
            String goalPath="/api/v1/goals/"+mapper.readTree(goal.body()).get("id").asString();
            assertThat(b.request("POST",goalPath+"/progress",Map.of("submissionId",UUID.randomUUID(),"quantity",1,"effectiveDate","2026-11-02"),true).statusCode()).isEqualTo(201);
            var task=b.request("POST","/api/v1/tasks",Map.of("title","Read","dueDate","2026-11-02"),true);
            assertThat(task.statusCode()).isEqualTo(201);
            var body=mapper.readTree(task.body());String taskPath="/api/v1/tasks/"+body.get("id").asString();
            assertThat(b.request("POST",taskPath+"/complete",Map.of("version",body.get("version").asLong()),true).statusCode()).isEqualTo(200);
            assertThat(b.request("PATCH","/api/v1/me",Map.of("timezone","UTC"),true).statusCode()).isEqualTo(200);
            assertThat(b.request("GET",goalPath,null,false).statusCode()).isEqualTo(200);
            assertThat(b.request("POST",taskPath+"/reopen",Map.of("version",1),false).statusCode()).isEqualTo(403);
            assertThat(b.request("POST","/api/v1/habits",Map.of("title","Not implemented"),true).statusCode()).isEqualTo(403);
        }
    }
    @Test void backwardsClockCannotBackdateTimezonePolicy() {
        UUID owner=user("UTC");calendar.beginTracking(owner);zone(owner,"Asia/Tokyo");
        now.set(now.get().minusSeconds(1));
        assertThatThrownBy(() -> zone(owner,"Asia/Almaty")).hasMessageContaining("clock moved behind");
        assertThat(policies(owner)).hasSize(2);
        assertThat(accounts.profile(owner).timezone()).isEqualTo("Asia/Tokyo");
    }

}
