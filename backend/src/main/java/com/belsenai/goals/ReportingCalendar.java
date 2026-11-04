package com.belsenai.goals;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import com.belsenai.common.error.ApiException;
import com.belsenai.identity.entity.User;
import com.belsenai.identity.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Call inside a transaction after locking the owner. Weeks are immutable once reserved. */
@Service
public class ReportingCalendar {
    private final ReportingWeekRepository weeks;
    private final UserRepository users;
    private final Clock clock;
    public ReportingCalendar(ReportingWeekRepository weeks, UserRepository users, Clock clock) {
        this.weeks=weeks; this.users=users; this.clock=clock;
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
        return reserve(owner, monday(LocalDate.ofInstant(now, ZoneId.of(owner.getTimezone()))));
    }
    public ReportingWeek reserve(User owner, LocalDate monday) {
        if (monday.getDayOfWeek()!=DayOfWeek.MONDAY) { throw new ApiException(400,"weekStart must be Monday."); }
        var existing=weeks.findByOwnerIdAndWeekStart(owner.getId(),monday);
        if(existing.isPresent()) { return existing.get(); }
        ZoneId zone=ZoneId.of(owner.getTimezone());
        ReportingWeek week=new ReportingWeek(); week.id=UUID.randomUUID(); week.ownerId=owner.getId();
        week.weekStart=monday; week.timezone=zone.getId();
        week.startsAt=monday.atStartOfDay(zone).toInstant(); week.endsAt=monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        // A timezone transition may shorten the next week's beginning, never extend a frozen week.
        for (ReportingWeek other: weeks.findByOwnerIdOrderByWeekStartAsc(owner.getId())) {
            if(other.weekStart.isBefore(monday) && other.endsAt.isAfter(week.startsAt)) { week.startsAt=other.endsAt; }
            if(other.weekStart.isAfter(monday) && other.startsAt.isBefore(week.endsAt)) { week.endsAt=other.startsAt; }
        }
        if(!week.endsAt.isAfter(week.startsAt)) { throw new ApiException(409,"Reporting week conflicts with an existing frozen week."); }
        return weeks.saveAndFlush(week);
    }
    public void open(ReportingWeek week) {
        openAt(week,now());
    }
    public void openAt(ReportingWeek week, Instant now) {
        if(now.isBefore(week.startsAt) || !now.isBefore(week.endsAt)) { throw new ApiException(409,"Progress changes require an open reporting week."); }
    }
    public GoalDtos.WeekView view(ReportingWeek w) { return new GoalDtos.WeekView(w.weekStart,w.timezone,w.startsAt,w.endsAt); }
}
