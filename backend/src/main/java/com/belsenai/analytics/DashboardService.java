package com.belsenai.analytics;

import java.time.*;
import java.util.*;
import com.belsenai.common.error.ApiException;
import com.belsenai.goals.ReportingCalendar;
import com.belsenai.habits.HabitRules;
import com.belsenai.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DashboardService {
    private final DashboardReader reader;
    private final ReportingCalendar calendar;
    private final UserRepository users;
    private final Clock clock;
    public DashboardService(DashboardReader reader,ReportingCalendar calendar,UserRepository users,Clock clock) {
        this.reader=reader;this.calendar=calendar;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public DashboardDtos.Dashboard dashboard(UUID owner,LocalDate requestedWeek,Instant requestedCutoff,int page,int size) {
        DashboardRules.page(page,size);
        if(requestedCutoff!=null && requestedWeek==null) throw new ApiException(400,"weekStart is required with cutoff.");
        if(requestedWeek!=null) DashboardRules.week(requestedWeek);
        Instant now=clock.instant();
        var user=users.findById(owner).orElseThrow(() -> new ApiException(401,"Account is unavailable."));
        ReportingCalendar.WeekWindow week;
        boolean history=calendar.hasHistory(owner);
        try {
            var resolved=requestedWeek==null ? calendar.readCurrent(owner,now) : calendar.readWeek(owner,requestedWeek);
            if(resolved.isPresent()) week=resolved.get();
            else if(!history) {
                ZoneId zone=ZoneId.of(user.getTimezone());LocalDate monday=ReportingCalendar.monday(LocalDate.ofInstant(now,zone));
                if(requestedWeek!=null && requestedWeek.isAfter(monday)) throw new ApiException(400,"Future reporting weeks are unavailable.");
                if(requestedWeek!=null && !requestedWeek.equals(monday)) throw new ApiException(404,"Reporting timezone history is unavailable for this period.");
                week=new ReportingCalendar.WeekWindow(null,monday,zone.getId(),monday.atStartOfDay(zone).toInstant(),monday.plusWeeks(1).atStartOfDay(zone).toInstant());
            } else {
                throw new ApiException(requestedWeek==null ? 409 : 404,requestedWeek==null ? "No current reporting interval exists during this timezone transition." : "Reporting timezone history is unavailable for this period.");
            }
            ZoneId.of(week.timezone());
        } catch(DateTimeException invalidCalendar) {
            throw new ApiException(409,"Reporting calendar configuration is invalid.");
        }
        Instant cutoff=DashboardRules.cutoff(requestedCutoff,now,week.startsAt(),week.endsAt());
        Instant bound=DashboardRules.sqlBound(cutoff);
        var taskCounts=reader.tasks(owner,week,bound);
        var tasks=new DashboardDtos.Tasks(taskCounts.scheduled(),taskCounts.completed(),taskCounts.scheduled()-taskCounts.completed(),
                DashboardRules.percent(taskCounts.completed(),taskCounts.scheduled()),taskCounts.scheduled()==0 ? "NO_SCHEDULED_TASKS" : null);
        var days=new ArrayList<ReportingCalendar.DayWindow>();
        for(int i=0;i<7;i++) calendar.readDay(week,week.weekStart().plusDays(i)).filter(day -> day.startsAt().isBefore(cutoff)).ifPresent(days::add);
        var habits=HabitRules.consistencyFromDays(reader.habits(owner,days,bound));
        var goals=reader.goals(owner,week,bound,page,size);
        var period=new DashboardDtos.Period(week.weekStart(),week.timezone(),week.startsAt(),week.endsAt(),cutoff,true,
                week.id()!=null ? "FROZEN" : history ? "RECONSTRUCTED" : "UNFROZEN",
                now.isBefore(week.endsAt()) ? "PROVISIONAL" : "CLOSED_UNFINALIZED",1);
        return new DashboardDtos.Dashboard(period,tasks,habits,goals);
    }
}
