package com.belsenai.goals;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import com.belsenai.common.error.ApiException;
import com.belsenai.identity.entity.User;
import com.belsenai.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Call inside a transaction after locking the owner. Weeks are immutable once reserved. */
@Service
public class ReportingCalendar {
    private final ReportingWeekRepository weeks;
    private final UserRepository users;
    private final Clock clock;
    private final ReportingTimezoneRevisionRepository revisions;
    public ReportingCalendar(ReportingWeekRepository weeks, UserRepository users, Clock clock, ReportingTimezoneRevisionRepository revisions) {
        this.weeks=weeks; this.users=users; this.clock=clock; this.revisions=revisions;
    }
    public User lockOwner(UUID owner) { return users.lockReportingOwner(owner).orElseThrow(() -> new ApiException(401,"Account is unavailable.")); }
    public Instant now() { return clock.instant(); }
    public static LocalDate monday(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    public ReportingWeek current(User owner) {
        return current(owner,now());
    }
    public ReportingWeek current(User owner, Instant now) {
        var active=weeks.findByOwnerIdOrderByWeekStartAsc(owner.getId()).stream()
                .filter(w -> !now.isBefore(w.startsAt) && now.isBefore(w.endsAt)).findFirst();
        if (active.isPresent()) { return active.get(); }
        var policies=revisions.findByOwnerIdOrderBySequenceAsc(owner.getId());
        if(!policies.isEmpty()) {
            for(int i=policies.size()-1;i>=0;i--) {
                var candidate=monday(LocalDate.ofInstant(now,ZoneId.of(policies.get(i).timezone)));
                var policy=policyAt(policies,candidate);
                if(policy.isPresent() && policy.get().sequence==policies.get(i).sequence) {
                    var selected=reserve(owner,candidate); openAt(selected,now); return selected;
                }
            }
            throw new ApiException(409,"No open reporting interval exists during this timezone transition.");
        }
        return reserve(owner, monday(LocalDate.ofInstant(now, ZoneId.of(owner.getTimezone()))));
    }
    public ReportingWeek reserve(User owner, LocalDate monday) {
        if (monday.getDayOfWeek()!=DayOfWeek.MONDAY) { throw new ApiException(400,"weekStart must be Monday."); }
        var existing=weeks.findByOwnerIdAndWeekStart(owner.getId(),monday);
        if(existing.isPresent()) { return existing.get(); }
        var policies=revisions.findByOwnerIdOrderBySequenceAsc(owner.getId());
        WeekWindow window;
        if(policies.isEmpty()) {
            window=window(monday,owner.getTimezone(),weeks.findByOwnerIdOrderByWeekStartAsc(owner.getId()),policies);
        } else {
            var policy=policyAt(policies,monday).orElseThrow(() -> new ApiException(409,"Reporting timezone history is unavailable before tracking began."));
            window=window(monday,policy.timezone,weeks.findByOwnerIdOrderByWeekStartAsc(owner.getId()),policies);
        }
        ReportingWeek week=new ReportingWeek(); week.id=UUID.randomUUID(); week.ownerId=owner.getId();
        week.weekStart=monday; week.timezone=window.timezone(); week.startsAt=window.startsAt(); week.endsAt=window.endsAt();
        return weeks.saveAndFlush(week);
    }
    /** Called by the future habit creation transaction, never by a history GET. */
    @Transactional
    public GoalDtos.WeekView beginTracking(UUID ownerId) {
        User owner=lockOwner(ownerId); Instant acceptedAt=now();
        var policies=revisions.findByOwnerIdOrderBySequenceAsc(ownerId);
        if(policies.isEmpty()) {
            var current=current(owner,acceptedAt); openAt(current,acceptedAt);
            appendPolicy(ownerId,1,current.weekStart,owner.getTimezone(),acceptedAt);
            return view(current);
        }
        return view(current(owner,acceptedAt));
    }

    /** Caller holds the account lock and updates the profile in the same transaction. */
    public void changeTimezonePolicy(User owner,String timezone,Instant acceptedAt) {
        var policies=revisions.findByOwnerIdOrderBySequenceAsc(owner.getId());
        if(policies.isEmpty()) { return; } // Preserve pre-tracking goal/task behavior.
        var current=current(owner,acceptedAt); openAt(current,acceptedAt);
        LocalDate effective=current.weekStart.plusWeeks(1);
        // Reject, rather than silently delay adoption past a frozen future reservation.
        boolean conflict=weeks.findByOwnerIdOrderByWeekStartAsc(owner.getId()).stream()
                .anyMatch(w -> !w.weekStart.isBefore(effective) && !w.timezone.equals(timezone));
        if(conflict) { throw new ApiException(409,"Requested timezone conflicts with a frozen future reporting week. Keep the current timezone until those reservations have elapsed."); }
        var last=policies.getLast();
        if(acceptedAt.isBefore(last.recordedAt) || effective.isBefore(last.effectiveWeekStart)) {
            throw new ApiException(409,"Reporting clock moved behind the last accepted timezone change. Retry later.");
        }
        appendPolicy(owner.getId(),last.sequence+1,effective,timezone,acceptedAt);
    }
    private void appendPolicy(UUID owner,long sequence,LocalDate effective,String timezone,Instant acceptedAt) {
        ReportingTimezoneRevision revision=new ReportingTimezoneRevision();
        revision.id=UUID.randomUUID(); revision.ownerId=owner; revision.sequence=sequence;
        revision.effectiveWeekStart=effective; revision.timezone=timezone; revision.recordedAt=acceptedAt;
        revisions.save(revision);
    }
    private Optional<ReportingTimezoneRevision> policyAt(List<ReportingTimezoneRevision> policies,LocalDate monday) {
        return policies.stream().filter(p -> !p.effectiveWeekStart.isAfter(monday))
                .max(Comparator.comparing((ReportingTimezoneRevision p) -> p.effectiveWeekStart).thenComparingLong(p -> p.sequence));
    }
    public record WeekWindow(UUID id,LocalDate weekStart,String timezone,Instant startsAt,Instant endsAt) { }
    public record DayWindow(WeekWindow week,LocalDate localDate,Instant startsAt,Instant endsAt) { }

    /** Pure database reads: an absent pre-baseline week has no reconstructable timezone. */
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Optional<WeekWindow> resolveWeek(UUID ownerId,LocalDate monday) {
        if(monday.getDayOfWeek()!=DayOfWeek.MONDAY) { throw new ApiException(400,"weekStart must be Monday."); }
        var stored=weeks.findByOwnerIdOrderByWeekStartAsc(ownerId);
        var existing=stored.stream().filter(w -> w.weekStart.equals(monday)).findFirst();
        if(existing.isPresent()) {
            var w=existing.get(); return Optional.of(new WeekWindow(w.id,w.weekStart,w.timezone,w.startsAt,w.endsAt));
        }
        var policies=revisions.findByOwnerIdOrderBySequenceAsc(ownerId);
        return policyAt(policies,monday).map(p -> window(monday,p.timezone,stored,policies));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Optional<DayWindow> resolveDay(UUID ownerId,LocalDate date) {
        return resolveWeek(ownerId,monday(date)).flatMap(w -> {
            ZoneId zone=ZoneId.of(w.timezone());
            Instant start=date.atStartOfDay(zone).toInstant(), end=date.plusDays(1).atStartOfDay(zone).toInstant();
            if(start.isBefore(w.startsAt())) start=w.startsAt();
            if(end.isAfter(w.endsAt())) end=w.endsAt();
            return end.isAfter(start) ? Optional.of(new DayWindow(w,date,start,end)) : Optional.empty();
        });
    }
    private WeekWindow window(LocalDate monday,String timezone,List<ReportingWeek> stored,
            List<ReportingTimezoneRevision> policies) {
        ZoneId zone=ZoneId.of(timezone);
        Instant start=monday.atStartOfDay(zone).toInstant(), end=monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        // A missing previous week still has an obligation window. Its end cannot be moved by a new policy.
        var previous=policyAt(policies,monday.minusWeeks(1));
        if(previous.isPresent()) {
            Instant previousEnd=monday.atStartOfDay(ZoneId.of(previous.get().timezone)).toInstant();
            if(previousEnd.isAfter(start)) start=previousEnd;
        }
        for(var other:stored) {
            if(other.weekStart.isBefore(monday) && other.endsAt.isAfter(start)) start=other.endsAt;
            if(other.weekStart.isAfter(monday) && other.startsAt.isBefore(end)) end=other.startsAt;
        }
        if(!end.isAfter(start)) throw new ApiException(409,"Reporting week conflicts with an existing frozen week.");
        return new WeekWindow(null,monday,timezone,start,end);
    }
    public void open(ReportingWeek week) {
        openAt(week,now());
    }
    public void openAt(ReportingWeek week, Instant now) {
        if(now.isBefore(week.startsAt) || !now.isBefore(week.endsAt)) { throw new ApiException(409,"Progress changes require an open reporting week."); }
    }
    public GoalDtos.WeekView view(ReportingWeek w) { return new GoalDtos.WeekView(w.weekStart,w.timezone,w.startsAt,w.endsAt); }
}
