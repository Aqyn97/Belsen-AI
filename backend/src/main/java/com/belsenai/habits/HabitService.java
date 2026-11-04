package com.belsenai.habits;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.belsenai.common.error.ApiException;
import com.belsenai.goals.*;
import com.belsenai.identity.entity.User;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true)
public class HabitService {
    private final HabitRepository habits;
    private final HabitScheduleVersionRepository schedules;
    private final HabitCheckinRepository checkins;
    private final HabitCheckinEventRepository events;
    private final ReportingCalendar calendar;
    private static final Pageable FIRST=PageRequest.of(0,1);
    public HabitService(HabitRepository habits,HabitScheduleVersionRepository schedules,
            HabitCheckinRepository checkins,HabitCheckinEventRepository events,ReportingCalendar calendar) {
        this.habits=habits;this.schedules=schedules;this.checkins=checkins;this.events=events;this.calendar=calendar;
    }
    @Transactional
    public HabitDtos.View create(UUID owner,HabitDtos.Create request) {
        User user=calendar.lockOwner(owner);Instant at=acceptance();
        var week=calendar.beginTracking(user,at);LocalDate today=LocalDate.ofInstant(at,ZoneId.of(week.getTimezone()));
        HabitRules.dates(request.startDate(),request.endDate());
        if(request.startDate().isBefore(today)) throw new ApiException(400,"Habit startDate must be today or later.");
        Habit h=new Habit();h.id=UUID.randomUUID();h.ownerId=owner;h.title=title(request.title());h.description=request.description();
        h.createdAt=at;h.updatedAt=at;h.revisionSequence=1;h=habits.save(h);
        appendSchedule(h,request.startDate(),request.startDate(),request.endDate(),HabitRules.mask(request.weekdays()),true,at,week.getId());
        habits.flush();return view(h,at);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public HabitDtos.View get(UUID owner,UUID id) { return view(owned(owner,id),acceptance()); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public HabitDtos.Page<HabitDtos.View> list(UUID owner,int page,int size,boolean archived) {
        HabitRules.page(page,size);var paging=PageRequest.of(page,size,Sort.by("createdAt","id"));
        var result=archived ? habits.findByOwnerIdAndArchivedAtIsNotNull(owner,paging) : habits.findByOwnerIdAndArchivedAtIsNull(owner,paging);
        Instant at=acceptance();return new HabitDtos.Page<>(result.stream().map(h -> view(h,at)).toList(),page,size,result.getTotalElements());
    }
    @Transactional
    public HabitDtos.View update(UUID owner,UUID id,HabitDtos.Update request) {
        calendar.lockOwner(owner);Habit h=locked(owner,id);Instant at=acceptance();version(h,request.version());active(h);ordered(h,at);
        if(request.title()==null && !request.descriptionProvided()) throw new ApiException(400,"Provide title or description.");
        String title=request.title()==null ? h.title : title(request.title());
        String description=request.descriptionProvided() ? request.description() : h.description;
        if(!Objects.equals(title,h.title) || !Objects.equals(description,h.description)) {
            h.title=title;h.description=description;touch(h,at);habits.flush();
        }
        return view(h,at);
    }
    @Transactional
    public HabitDtos.View schedule(UUID owner,UUID id,HabitDtos.Schedule request) {
        User user=calendar.lockOwner(owner);Habit h=locked(owner,id);Instant at=acceptance();version(h,request.version());active(h);ordered(h,at);
        var week=calendar.current(user,at);calendar.openAt(week,at);LocalDate today=LocalDate.ofInstant(at,ZoneId.of(week.getTimezone()));
        HabitRules.dates(request.startDate(),request.endDate());HabitRules.validDate(request.effectiveFrom());
        if(!request.effectiveFrom().isAfter(today)) throw new ApiException(409,"Schedule changes must take effect tomorrow or later.");
        touch(h,at);appendSchedule(h,request.effectiveFrom(),request.startDate(),request.endDate(),HabitRules.mask(request.weekdays()),true,at,week.getId());
        habits.flush();return view(h,at);
    }
    @Transactional
    public HabitDtos.View archive(UUID owner,UUID id,long expected) {
        User user=calendar.lockOwner(owner);Habit h=locked(owner,id);Instant at=acceptance();version(h,expected);ordered(h,at);
        if(h.archivedAt==null) {
            var week=calendar.current(user,at);calendar.openAt(week,at);
            h.archivedAt=at;h.archiveEffectiveDate=LocalDate.ofInstant(at,ZoneId.of(week.getTimezone())).plusDays(1);
            touch(h,at);appendSchedule(h,h.archiveEffectiveDate,h.archiveEffectiveDate,null,(short)0,false,at,week.getId());habits.flush();
        }
        return view(h,at);
    }
    enum Action { COMPLETED, UNDONE, REST_PLANNED, REST_CANCELLED }
    @Transactional
    public HabitDtos.DayView complete(UUID owner,UUID id,LocalDate date,HabitDtos.Action request) {
        return mutate(owner,id,date,request.weekStart(),request.version(),Action.COMPLETED,null);
    }
    @Transactional
    public HabitDtos.DayView undo(UUID owner,UUID id,LocalDate date,LocalDate weekStart,long version) {
        return mutate(owner,id,date,weekStart,version,Action.UNDONE,null);
    }
    @Transactional
    public HabitDtos.DayView rest(UUID owner,UUID id,LocalDate date,HabitDtos.Rest request) {
        return mutate(owner,id,date,request.weekStart(),request.version(),Action.REST_PLANNED,request.reason().strip());
    }
    @Transactional
    public HabitDtos.DayView cancelRest(UUID owner,UUID id,LocalDate date,LocalDate weekStart,long version) {
        return mutate(owner,id,date,weekStart,version,Action.REST_CANCELLED,null);
    }
    private HabitDtos.DayView mutate(UUID owner,UUID id,LocalDate date,LocalDate requestedWeek,long expected,Action action,String reason) {
        User user=calendar.lockOwner(owner);Habit h=locked(owner,id);Instant at=acceptance();version(h,expected);ordered(h,at);
        HabitRules.validDate(date);HabitRules.validDate(requestedWeek);
        if(!ReportingCalendar.monday(date).equals(requestedWeek)) throw new ApiException(400,"weekStart must be the Monday containing the requested date.");
        var current=calendar.current(user,at);calendar.openAt(current,at);
        boolean restAction=action==Action.REST_PLANNED || action==Action.REST_CANCELLED;
        if(!restAction && !current.getWeekStart().equals(requestedWeek)) throw new ApiException(409,"Check-ins require the current open reporting week.");
        var day=calendar.resolveDay(owner,date).orElseThrow(() -> new ApiException(409,"This date has no reporting interval."));
        if(restAction) {
            if(!at.isBefore(day.startsAt())) throw new ApiException(409,"Rest must be planned or cancelled before the day starts.");
        } else {
            if(at.isBefore(day.startsAt())) throw new ApiException(409,"Future days cannot be completed or undone.");
        }
        var schedule=effective(h,date,at,true);
        boolean scheduled=scheduled(schedule,date);
        var found=checkins.findByHabitIdAndOwnerIdAndLocalDate(id,owner,date);var c=found.orElse(null);
        if(!scheduled && !(action==Action.REST_CANCELLED && c!=null)) throw new ApiException(409,"This habit is not scheduled on that date.");
        if(c!=null && !c.reportingWeekId.equals(day.week().id())) throw new ApiException(409,"The occurrence belongs to a different frozen calendar.");
        if(c!=null && at.isBefore(c.updatedAt)) throw new ApiException(409,"Reporting clock moved behind the last check-in.");
        boolean completed=c!=null && c.completed,rest=c!=null && c.restPlanned;
        if(action==Action.COMPLETED && rest) throw new ApiException(409,"A planned rest day cannot be completed.");
        if(action==Action.REST_PLANNED && completed) throw new ApiException(409,"A completed day cannot become planned rest.");
        boolean newCompleted=action==Action.COMPLETED || (action!=Action.UNDONE && completed);
        boolean newRest=action==Action.REST_PLANNED || (action!=Action.REST_CANCELLED && rest);
        if(completed==newCompleted && rest==newRest) return dayView(h,date,at,true);
        var week=calendar.reserve(user,requestedWeek);
        if(c==null) {
            c=new HabitCheckin();c.id=UUID.randomUUID();c.habitId=id;c.ownerId=owner;c.localDate=date;
            c.reportingWeekId=week.getId();c.scheduleVersionId=schedule.id;c.createdAt=at;c.eventSequence=1;
        } else c.eventSequence++;
        c.completed=newCompleted;c.restPlanned=newRest;c.updatedAt=at;c=checkins.save(c);
        HabitCheckinEvent event=new HabitCheckinEvent();event.id=UUID.randomUUID();event.checkinId=c.id;event.habitId=id;event.ownerId=owner;
        event.sequence=c.eventSequence;event.action=action.name();event.acceptedAt=at;event.completed=newCompleted;event.restPlanned=newRest;
        event.reason=reason;event.reportingWeekId=current.getId();touch(h,at);events.save(event);habits.flush();
        return dayView(h,date,at,true);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public HabitDtos.History history(UUID owner,UUID id,LocalDate from,LocalDate to,Instant requestedCutoff,
            int page,int size,int actionPage,int schedulePage) {
        Habit h=owned(owner,id);HabitRules.range(from,to);HabitRules.page(page,size);HabitRules.page(actionPage,size);HabitRules.page(schedulePage,size);
        Instant now=calendar.now(),cutoff=requestedCutoff==null ? now.truncatedTo(ChronoUnit.MICROS) : requestedCutoff;
        if(cutoff.isAfter(now)) throw new ApiException(400,"History cutoff cannot be in the future.");
        boolean inclusive=requestedCutoff==null;
        if(h.createdAt.isAfter(cutoff) || (!inclusive && h.createdAt.equals(cutoff))) throw new ApiException(404,"Habit did not exist at this cutoff.");
        int total=(int)ChronoUnit.DAYS.between(from,to)+1;long offset=(long)page*size;
        var days=new ArrayList<HabitDtos.DayView>();
        for(long index=offset;index<total && index<offset+size;index++) days.add(dayView(h,from.plusDays(index),cutoff,inclusive));
        var actionRows=events.history(id,owner,from,to,queryCutoff(cutoff,inclusive),inclusive,PageRequest.of(actionPage,size,Sort.by("acceptedAt","checkinId","sequence")));
        var actionViews=actionRows.stream().map(e -> new HabitDtos.EventView(e.id,checkins.findByIdAndOwnerId(e.checkinId,owner).orElseThrow().localDate,e.sequence,e.action,e.acceptedAt,e.completed,e.restPlanned,e.reason,e.reportingWeekId)).toList();
        var versions=schedules.history(id,owner,to,queryCutoff(cutoff,inclusive),inclusive,PageRequest.of(schedulePage,size,Sort.by("sequence")));
        return new HabitDtos.History(id,cutoff,!inclusive,new HabitDtos.Page<>(days,page,size,total),
                new HabitDtos.Page<>(actionViews,actionPage,size,actionRows.getTotalElements()),
                new HabitDtos.Page<>(versions.stream().map(this::scheduleView).toList(),schedulePage,size,versions.getTotalElements()));
    }
    private HabitDtos.DayView dayView(Habit h,LocalDate date,Instant cutoff,boolean inclusive) {
        var context=calendar.resolveDay(h.ownerId,date).orElse(null);
        boolean started=context!=null && (context.startsAt().isBefore(cutoff) || (inclusive && context.startsAt().equals(cutoff)));
        var schedule=effective(h,date,cutoff,inclusive);boolean scheduled=scheduled(schedule,date);
        var c=checkins.findByHabitIdAndOwnerIdAndLocalDate(h.id,h.ownerId,date).orElse(null);
        var states=c==null ? List.<HabitCheckinEvent>of() : events.state(c.id,h.ownerId,queryCutoff(cutoff,inclusive),inclusive,FIRST);
        var state=states.isEmpty() ? null : states.getFirst();
        boolean complete=state!=null && state.completed,rest=state!=null && state.restPlanned;
        return new HabitDtos.DayView(date,started,scheduled,complete,rest,scheduled && rest,started && scheduled && !rest,
                rest ? state.reason : null,h.version,schedule==null ? null : scheduleView(schedule),!inclusive && !started ? null : context);
    }
    private HabitScheduleVersion effective(Habit h,LocalDate date,Instant cutoff,boolean inclusive) {
        var values=schedules.effective(h.id,h.ownerId,date,queryCutoff(cutoff,inclusive),inclusive,FIRST);return values.isEmpty() ? null : values.getFirst();
    }
    private boolean scheduled(HabitScheduleVersion s,LocalDate date) {
        return s!=null && s.enabled && !date.isBefore(s.startDate) && (s.endDate==null || !date.isAfter(s.endDate)) && (s.weekdaysMask & (1 << (date.getDayOfWeek().getValue()-1)))!=0;
    }
    private void appendSchedule(Habit h,LocalDate effective,LocalDate start,LocalDate end,short mask,boolean enabled,Instant at,UUID week) {
        var s=new HabitScheduleVersion();s.id=UUID.randomUUID();s.habitId=h.id;s.ownerId=h.ownerId;s.sequence=h.revisionSequence;
        s.effectiveFrom=effective;s.startDate=start;s.endDate=end;s.weekdaysMask=mask;s.enabled=enabled;s.recordedAt=at;s.reportingWeekId=week;schedules.save(s);
    }
    private HabitDtos.ScheduleView scheduleView(HabitScheduleVersion s) {
        return new HabitDtos.ScheduleView(s.id,s.sequence,s.effectiveFrom,s.startDate,s.endDate,HabitRules.weekdays(s.weekdaysMask),s.enabled,s.recordedAt);
    }
    private HabitDtos.View view(Habit h,Instant at) {
        var context=calendar.resolveCurrent(h.ownerId,at).orElse(null);
        LocalDate today=context==null ? null : LocalDate.ofInstant(at,ZoneId.of(context.timezone()));
        var current=today==null ? null : effective(h,today,at,true);
        // Show the most recently accepted pending revision; paginated history contains all revisions.
        var pending=today==null ? List.<HabitScheduleVersion>of() : schedules.pending(h.id,h.ownerId,today,current==null ? 0 : current.sequence,at,FIRST);
        return new HabitDtos.View(h.id,h.title,h.description,h.archivedAt==null ? "ACTIVE" : "ARCHIVED",h.archivedAt,h.archiveEffectiveDate,
                h.version,h.createdAt,h.updatedAt,context,current==null ? null : scheduleView(current),pending.isEmpty() ? null : scheduleView(pending.getFirst()));
    }
    private Habit owned(UUID owner,UUID id) { return habits.findByIdAndOwnerId(id,owner).orElseThrow(() -> new ApiException(404,"Habit not found.")); }
    private Habit locked(UUID owner,UUID id) { return habits.lockOwned(id,owner).orElseThrow(() -> new ApiException(404,"Habit not found.")); }
    private void active(Habit h) { if(h.archivedAt!=null) throw new ApiException(409,"Archived habits cannot be edited."); }
    private void version(Habit h,long expected) { if(h.version!=expected) throw new ApiException(409,"Resource version is stale. Reload and retry."); }
    private void ordered(Habit h,Instant at) { if(at.isBefore(h.updatedAt)) throw new ApiException(409,"Reporting clock moved behind the last habit change."); }
    private void touch(Habit h,Instant at) { h.updatedAt=at;h.revisionSequence++; }
    private String title(String value) { if(value==null || value.isBlank()) throw new ApiException(400,"Title cannot be blank.");return value.strip(); }
    // JDBC/PostgreSQL timestamps have microsecond precision. Ceil an exclusive bound so
    // a sub-microsecond cutoff includes every persisted tick strictly before it.
    private Instant queryCutoff(Instant cutoff,boolean inclusive) {
        Instant tick=cutoff.truncatedTo(ChronoUnit.MICROS);
        return inclusive || tick.equals(cutoff) ? tick : tick.plusNanos(1000);
    }
    private Instant acceptance() { return calendar.now().truncatedTo(ChronoUnit.MICROS); }
}
