package com.belsenai.goals;

import java.time.*;
import java.util.*;
import com.belsenai.common.error.ApiException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(readOnly=true)
public class GoalService {
    private final GoalRepository goals;
    private final GoalProgressRepository progress;
    private final GoalPlanRepository plans;
    private final ReportingWeekRepository weeks;
    private final ReportingCalendar calendar;
    public GoalService(GoalRepository goals, GoalProgressRepository progress, GoalPlanRepository plans,
            ReportingWeekRepository weeks, ReportingCalendar calendar) {
        this.goals=goals; this.progress=progress; this.plans=plans; this.weeks=weeks; this.calendar=calendar;
    }
    @Transactional
    public GoalDtos.View create(UUID owner, GoalDtos.Create request) {
        GoalRules.dates(request.startDate(),request.deadline());
        Goal goal=new Goal(); goal.id=UUID.randomUUID(); goal.ownerId=owner;
        goal.title=text(request.title()); goal.description=request.description(); goal.target=request.target();
        goal.unit=text(request.unit()); goal.startDate=request.startDate(); goal.deadline=request.deadline();
        goal.createdAt=calendar.now(); goal.updatedAt=goal.createdAt;
        return view(goals.saveAndFlush(goal));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, propagation=Propagation.REQUIRES_NEW)
    public GoalDtos.View get(UUID owner, UUID id) { return view(owned(owner,id)); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, propagation=Propagation.REQUIRES_NEW)
    public GoalDtos.PageResponse<GoalDtos.View> list(UUID owner,int page,int size) {
        var result=goals.findByOwnerId(owner,pageable(page,size,"createdAt"));
        return new GoalDtos.PageResponse<>(result.map(this::view).getContent(),page,size,result.getTotalElements());
    }
    @Transactional
    public GoalDtos.View update(UUID owner, UUID id, GoalDtos.Update request) {
        calendar.lockOwner(owner); Goal goal=locked(owner,id); active(goal); version(goal.version,request.version());
        if(request.unit()!=null && !text(request.unit()).equals(goal.unit)
                && (progress.existsByGoalId(id) || plans.existsByGoalId(id))) {
            throw new ApiException(409,"Unit cannot change after progress or planning exists.");
        }
        if(Boolean.TRUE.equals(request.clearDescription()) && request.description()!=null || Boolean.TRUE.equals(request.clearStartDate()) && request.startDate()!=null
                || Boolean.TRUE.equals(request.clearDeadline()) && request.deadline()!=null) { throw new ApiException(400,"Conflicting clear and value fields."); }
        if(request.title()!=null) { goal.title=text(request.title()); }
        if(request.description()!=null || Boolean.TRUE.equals(request.clearDescription())) { goal.description=request.description(); }
        if(request.target()!=null) { goal.target=request.target(); }
        if(request.unit()!=null) { goal.unit=text(request.unit()); }
        if(request.startDate()!=null || Boolean.TRUE.equals(request.clearStartDate())) { goal.startDate=request.startDate(); }
        if(request.deadline()!=null || Boolean.TRUE.equals(request.clearDeadline())) { goal.deadline=request.deadline(); }
        GoalRules.dates(goal.startDate,goal.deadline); touch(goal); goals.flush(); return view(goal);
    }
    @Transactional
    public GoalDtos.View archive(UUID owner, UUID id, long expectedVersion) {
        calendar.lockOwner(owner); Goal goal=locked(owner,id); version(goal.version,expectedVersion);
        if(goal.archivedAt==null) { goal.archivedAt=calendar.now(); touch(goal); goals.flush(); }
        return view(goal);
    }
    @Transactional
    public GoalDtos.Recorded record(UUID owner,UUID id,GoalDtos.Progress request) {
        var user=calendar.lockOwner(owner); Goal goal=locked(owner,id);
        Instant acceptedAt=calendar.now();
        var retry=progress.findByGoalIdAndSubmissionId(id,request.submissionId());
        if(retry.isPresent()) {
            var existing=retry.get();
            if(existing.quantity.compareTo(request.quantity())!=0 || !existing.effectiveDate.equals(request.effectiveDate())) {
                throw new ApiException(409,"Submission identifier was already used with different progress.");
            }
            return new GoalDtos.Recorded(entryView(existing),false);
        }
        active(goal); var week=calendar.current(user,acceptedAt); calendar.openAt(week,acceptedAt);
        LocalDate today=LocalDate.ofInstant(acceptedAt,ZoneId.of(week.timezone));
        if(request.effectiveDate().isBefore(week.weekStart) || !request.effectiveDate().isBefore(week.weekStart.plusWeeks(1))) {
            throw new ApiException(409,"Progress must belong to the open reporting week.");
        }
        if(request.effectiveDate().isAfter(today)) { throw new ApiException(400,"Future progress dates are not allowed."); }
        if(goal.startDate!=null && request.effectiveDate().isBefore(goal.startDate)) { throw new ApiException(400,"Progress precedes goal start date."); }
        GoalProgressEntry entry=new GoalProgressEntry(); entry.id=UUID.randomUUID(); entry.goalId=id; entry.ownerId=owner;
        entry.reportingWeekId=week.id; entry.submissionId=request.submissionId(); entry.quantity=request.quantity();
        entry.effectiveDate=request.effectiveDate(); entry.createdAt=acceptedAt;
        entry=progress.saveAndFlush(entry); touch(goal,acceptedAt); goals.flush();
        return new GoalDtos.Recorded(entryView(entry),true);
    }
    public GoalDtos.PageResponse<GoalDtos.ProgressView> history(UUID owner, UUID id,int page,int size) {
        owned(owner,id);
        var result=progress.findByGoalIdAndOwnerId(id,owner,pageable(page,size,"createdAt"));
        return new GoalDtos.PageResponse<>(result.map(this::entryView).getContent(),page,size,result.getTotalElements());
    }
    @Transactional
    public GoalDtos.ProgressView voidEntry(UUID owner,UUID goalId,UUID entryId) {
        calendar.lockOwner(owner); Goal goal=locked(owner,goalId);
        Instant acceptedAt=calendar.now();
        var entry=progress.findByIdAndGoalIdAndOwnerId(entryId,goalId,owner)
                .orElseThrow(() -> new ApiException(404,"Progress entry not found."));
        active(goal); calendar.openAt(week(entry.reportingWeekId),acceptedAt);
        if(entry.voidedAt==null) { entry.voidedAt=acceptedAt; progress.flush(); touch(goal,acceptedAt); goals.flush(); }
        return entryView(entry);
    }
    @Transactional
    public GoalDtos.PlanView plan(UUID owner, UUID id,LocalDate weekStart,GoalDtos.Plan request) {
        var user=calendar.lockOwner(owner); Goal goal=locked(owner,id);
        Instant acceptedAt=calendar.now(); active(goal);
        // Freeze the current reporting calendar even when planning only a future week.
        var current=calendar.current(user,acceptedAt); calendar.openAt(current,acceptedAt);
        var week=calendar.reserve(user,weekStart);
        if(!acceptedAt.isBefore(week.endsAt)) { throw new ApiException(409,"Closed weekly plans cannot change."); }
        var found=plans.findByGoalIdAndReportingWeekId(id,week.id);
        GoalWeeklyPlan plan;
        if(found.isPresent()) {
            plan=found.get();
            if(!acceptedAt.isBefore(week.startsAt)) { throw new ApiException(409,"A started weekly plan is fixed."); }
            if(request.version()==null) { throw new ApiException(409,"Expected plan version is required."); }
            version(plan.version,request.version()); plan.plannedUnits=request.plannedUnits(); plan.updatedAt=acceptedAt;
        } else {
            if(request.version()!=null) { throw new ApiException(409,"Weekly plan does not exist at the supplied version."); }
            plan=new GoalWeeklyPlan(); plan.id=UUID.randomUUID(); plan.goalId=id; plan.ownerId=owner;
            plan.reportingWeekId=week.id; plan.plannedUnits=request.plannedUnits(); plan.createdAt=acceptedAt; plan.updatedAt=plan.createdAt;
        }
        plan=plans.saveAndFlush(plan); touch(goal,acceptedAt); goals.flush(); return planView(plan);
    }
    public void requireOwned(UUID owner, UUID id) { owned(owner,id); }
    public void requireActiveOwned(UUID owner, UUID id) { active(owned(owner,id)); }

    private Goal owned(UUID owner,UUID id) { return goals.findByIdAndOwnerId(id,owner).orElseThrow(() -> new ApiException(404,"Goal not found.")); }
    private Goal locked(UUID owner,UUID id) { return goals.lockOwned(id,owner).orElseThrow(() -> new ApiException(404,"Goal not found.")); }
    private void active(Goal goal) { if(goal.archivedAt!=null) { throw new ApiException(409,"Archived goals cannot be modified."); } }
    private void version(long actual,long expected) { if(actual!=expected) { throw new ApiException(409,"Resource version is stale. Reload and retry."); } }
    private void touch(Goal goal) { touch(goal,calendar.now()); }
    private void touch(Goal goal, Instant acceptedAt) { goal.updatedAt=acceptedAt; }
    private String text(String value) { if(value.isBlank()) { throw new ApiException(400,"Text cannot be blank."); } return value.strip(); }
    private ReportingWeek week(UUID id) { return weeks.findById(id).orElseThrow(); }
    private GoalDtos.ProgressView entryView(GoalProgressEntry p) {
        return new GoalDtos.ProgressView(p.id,p.submissionId,p.quantity,p.effectiveDate,p.createdAt,p.voidedAt,calendar.view(week(p.reportingWeekId)));
    }
    private GoalDtos.PlanView planView(GoalWeeklyPlan p) {
        var completed=progress.weeklyTotal(p.goalId,p.reportingWeekId);
        return new GoalDtos.PlanView(p.id,calendar.view(week(p.reportingWeekId)),p.plannedUnits,completed,GoalRules.percent(completed,p.plannedUnits),p.version);
    }
    private GoalDtos.View view(Goal g) {
        var total=progress.total(g.id);
        var weekly=plans.findByGoalId(g.id).stream().map(this::planView)
                .sorted(Comparator.comparing(p -> p.reportingWeek().weekStart())).toList();
        return new GoalDtos.View(g.id,g.title,g.description,g.target,g.unit,g.startDate,g.deadline,
                g.archivedAt==null?"ACTIVE":"ARCHIVED",g.archivedAt,g.version,total,GoalRules.percent(total,g.target),weekly);
    }
    private Pageable pageable(int page,int size,String order) {
        if(page<0 || size<1 || size>100) { throw new ApiException(400,"Use page >= 0 and size between 1 and 100."); }
        return PageRequest.of(page,size,Sort.by(order).ascending().and(Sort.by("id")));
    }
}
