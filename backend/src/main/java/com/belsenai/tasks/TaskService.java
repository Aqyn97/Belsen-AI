package com.belsenai.tasks;

import java.time.*;
import java.util.*;
import com.belsenai.common.error.ApiException;
import com.belsenai.goals.*;
import com.belsenai.identity.entity.User;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class TaskService {
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final GoalService goals;
    private final ReportingCalendar calendar;
    private final ReportingWeekRepository weeks;

    public TaskService(TaskRepository tasks,TaskEventRepository events,GoalService goals,
            ReportingCalendar calendar,ReportingWeekRepository weeks) {
        this.tasks=tasks; this.events=events; this.goals=goals; this.calendar=calendar; this.weeks=weeks;
    }

    @Transactional
    public TaskDtos.View create(UUID owner,TaskDtos.Create request) {
        User user=calendar.lockOwner(owner);
        ReportingWeek current=currentWeek(user);
        if(request.goalId()!=null) { goals.requireActiveOwned(owner,request.goalId()); }
        Task task=new Task();
        task.id=UUID.randomUUID(); task.ownerId=owner; task.title=title(request.title());
        task.description=request.description(); task.goalId=request.goalId();
        task.estimatedDurationMinutes=request.estimatedDurationMinutes(); task.status=TaskStatus.OPEN;
        task.createdAt=calendar.now(); task.updatedAt=task.createdAt; task.eventSequence=1;
        task=tasks.saveAndFlush(task);
        events.save(snapshot(task,TaskEventType.CREATED,current));
        if(request.dueDate()!=null) {
            assignFirstDate(task,request.dueDate(),user);
            task.currentDueDate=request.dueDate();
            append(task,TaskEventType.DUE_ASSIGNED,current);
        }
        tasks.flush();
        return view(task);
    }

    public TaskDtos.View get(UUID owner,UUID id) { return view(owned(owner,id)); }

    public TaskDtos.PageView list(UUID owner,int page,int size,TaskStatus status,boolean archived,
            UUID goalId,LocalDate dueDate,LocalDate from,LocalDate to) {
        if(page<0 || size<1 || size>100) { throw new ApiException(400,"Use page >= 0 and size between 1 and 100."); }
        if(dueDate!=null && (from!=null || to!=null) || from!=null && to!=null && from.isAfter(to)) {
            throw new ApiException(400,"Conflicting or reversed due-date filters.");
        }
        if(goalId!=null) { goals.requireOwned(owner,goalId); }
        Specification<Task> filter=(root,query,builder) -> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("ownerId"),owner));
            predicates.add(archived?builder.isNotNull(root.get("archivedAt")):builder.isNull(root.get("archivedAt")));
            if(status!=null) { predicates.add(builder.equal(root.get("status"),status)); }
            if(goalId!=null) { predicates.add(builder.equal(root.get("goalId"),goalId)); }
            if(dueDate!=null) { predicates.add(builder.equal(root.get("currentDueDate"),dueDate)); }
            if(from!=null) { predicates.add(builder.greaterThanOrEqualTo(root.get("currentDueDate"),from)); }
            if(to!=null) { predicates.add(builder.lessThanOrEqualTo(root.get("currentDueDate"),to)); }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=tasks.findAll(filter,PageRequest.of(page,size,Sort.by("createdAt").ascending().and(Sort.by("id"))));
        return new TaskDtos.PageView(result.map(this::view).getContent(),page,size,result.getTotalElements());
    }

    @Transactional
    public TaskDtos.View update(UUID owner,UUID id,TaskDtos.Update request) {
        User user=calendar.lockOwner(owner);
        Task task=locked(owner,id); version(task,request.version()); active(task);
        if(request.goalProvided() && request.goalId()!=null) { goals.requireActiveOwned(owner,request.goalId()); }
        String newTitle=request.title()==null?task.title:title(request.title());
        String newDescription=request.descriptionProvided()?request.description():task.description;
        UUID newGoal=request.goalProvided()?request.goalId():task.goalId;
        Integer newDuration=request.durationProvided()?request.duration():task.estimatedDurationMinutes;
        if(Objects.equals(task.title,newTitle) && Objects.equals(task.description,newDescription)
                && Objects.equals(task.goalId,newGoal) && Objects.equals(task.estimatedDurationMinutes,newDuration)) { return view(task); }
        task.title=newTitle; task.description=newDescription; task.goalId=newGoal; task.estimatedDurationMinutes=newDuration;
        append(task,TaskEventType.UPDATED,currentWeek(user)); tasks.flush();
        return view(task);
    }

    @Transactional
    public TaskDtos.View complete(UUID owner,UUID id,long expectedVersion) {
        User user=calendar.lockOwner(owner);
        Task task=locked(owner,id); version(task,expectedVersion); active(task);
        if(task.status==TaskStatus.COMPLETED) { return view(task); }
        task.status=TaskStatus.COMPLETED; task.completedAt=calendar.now();
        append(task,TaskEventType.COMPLETED,currentWeek(user)); tasks.flush(); return view(task);
    }

    @Transactional
    public TaskDtos.View reopen(UUID owner,UUID id,long expectedVersion) {
        User user=calendar.lockOwner(owner);
        Task task=locked(owner,id); version(task,expectedVersion); active(task);
        if(task.status==TaskStatus.OPEN) { return view(task); }
        task.status=TaskStatus.OPEN; task.completedAt=null;
        append(task,TaskEventType.REOPENED,currentWeek(user)); tasks.flush(); return view(task);
    }

    @Transactional
    public TaskDtos.View reschedule(UUID owner,UUID id,TaskDtos.Reschedule request) {
        User user=calendar.lockOwner(owner);
        Task task=locked(owner,id); version(task,request.version()); active(task);
        if(task.status==TaskStatus.COMPLETED) { throw new ApiException(409,"Reopen a completed task before rescheduling."); }
        if(Objects.equals(task.currentDueDate,request.dueDate())) { return view(task); }
        ReportingWeek current=currentWeek(user);
        boolean first=task.originalDueDate==null && request.dueDate()!=null;
        if(first) { assignFirstDate(task,request.dueDate(),user); }
        task.currentDueDate=request.dueDate();
        append(task,first?TaskEventType.DUE_ASSIGNED:TaskEventType.RESCHEDULED,current); tasks.flush();
        return view(task);
    }

    @Transactional
    public TaskDtos.View archive(UUID owner,UUID id,long expectedVersion) {
        User user=calendar.lockOwner(owner);
        Task task=locked(owner,id); version(task,expectedVersion);
        if(task.archivedAt!=null) { return view(task); }
        task.archivedAt=calendar.now();
        append(task,TaskEventType.ARCHIVED,currentWeek(user)); tasks.flush(); return view(task);
    }

    private ReportingWeek currentWeek(User user) {
        ReportingWeek current=calendar.current(user); calendar.open(current); return current;
    }

    private void assignFirstDate(Task task,LocalDate date,User owner) {
        ReportingWeek week=calendar.reserve(owner,ReportingCalendar.monday(date));
        if(!calendar.now().isBefore(week.getEndsAt())) { throw new ApiException(409,"First due date cannot belong to a closed reporting week."); }
        task.originalDueDate=date; task.originalReportingWeekId=week.getId();
    }

    private void append(Task task,TaskEventType type,ReportingWeek current) {
        task.eventSequence++;
        task.updatedAt=calendar.now();
        if(type==TaskEventType.COMPLETED) { task.completedAt=task.updatedAt; }
        if(type==TaskEventType.ARCHIVED) { task.archivedAt=task.updatedAt; }
        events.save(snapshot(task,type,current));
    }

    private TaskEvent snapshot(Task task,TaskEventType type,ReportingWeek current) {
        calendar.openAt(current,task.updatedAt);
        TaskEvent event=new TaskEvent(); event.id=UUID.randomUUID(); event.taskId=task.id; event.ownerId=task.ownerId;
        event.sequence=task.eventSequence; event.type=type; event.occurredAt=task.updatedAt;
        event.reportingWeekId=current.getId(); event.originalReportingWeekId=task.originalReportingWeekId;
        event.originalDueDate=task.originalDueDate; event.currentDueDate=task.currentDueDate;
        event.status=task.status; event.completedAt=task.completedAt; event.archivedAt=task.archivedAt;
        return event;
    }

    private Task owned(UUID owner,UUID id) { return tasks.findByIdAndOwnerId(id,owner).orElseThrow(() -> new ApiException(404,"Task not found.")); }
    private Task locked(UUID owner,UUID id) { return tasks.lockOwned(id,owner).orElseThrow(() -> new ApiException(404,"Task not found.")); }
    private void active(Task task) { if(task.archivedAt!=null) { throw new ApiException(409,"Archived tasks cannot be modified."); } }
    private void version(Task task,long expected) { if(task.version!=expected) { throw new ApiException(409,"Task version is stale. Reload and retry."); } }
    private String title(String value) { if(value.isBlank()) { throw new ApiException(400,"Title cannot be blank."); } return value.strip(); }
    private TaskDtos.View view(Task task) {
        TaskDtos.WeekView original=null;
        if(task.originalReportingWeekId!=null) {
            ReportingWeek week=weeks.findById(task.originalReportingWeekId).orElseThrow();
            original=new TaskDtos.WeekView(week.getId(),week.getWeekStart(),week.getTimezone(),week.getStartsAt(),week.getEndsAt());
        }
        return new TaskDtos.View(task.id,task.title,task.description,task.goalId,task.originalDueDate,task.currentDueDate,
                original,task.estimatedDurationMinutes,task.status,task.completedAt,task.archivedAt,
                task.createdAt,task.updatedAt,task.version,task.eventSequence);
    }
}
