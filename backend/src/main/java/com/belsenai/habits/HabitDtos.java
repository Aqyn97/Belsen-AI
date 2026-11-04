package com.belsenai.habits;

import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import com.belsenai.goals.ReportingCalendar;

public final class HabitDtos {
    private HabitDtos() { }
    public record Create(@NotBlank @Size(max=200) String title, @Size(max=2000) String description,
            @NotNull @Size(min=1,max=7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
            @NotNull LocalDate startDate, LocalDate endDate) { }
    public record Schedule(@NotNull @PositiveOrZero Long version, @NotNull LocalDate effectiveFrom,
            @NotNull @Size(min=1,max=7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
            @NotNull LocalDate startDate, LocalDate endDate) { }
    public static class Update {
        @com.fasterxml.jackson.annotation.JsonProperty @NotNull @PositiveOrZero private Long version;
        @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL)
        @Size(min=1,max=200) private String title;
        @Size(max=2000) private String description;
        private boolean descriptionProvided;
        @com.fasterxml.jackson.annotation.JsonSetter("description")
        public void setDescription(String value) { description=value; descriptionProvided=true; }
        public Long version() { return version; }
        public String title() { return title; }
        public String description() { return description; }
        public boolean descriptionProvided() { return descriptionProvided; }
    }
    public record Version(@NotNull @PositiveOrZero Long version) { }
    public record Action(@NotNull @PositiveOrZero Long version,@NotNull LocalDate weekStart) { }
    public record Rest(@NotNull @PositiveOrZero Long version,@NotNull LocalDate weekStart,
            @NotBlank @Size(max=500) String reason) { }
    public record ScheduleView(UUID id,long sequence,LocalDate effectiveFrom,LocalDate startDate,
            LocalDate endDate,List<Integer> weekdays,boolean enabled,Instant recordedAt) { }
    public record View(UUID id,String title,String description,String status,Instant archivedAt,
            LocalDate archiveEffectiveDate,long version,Instant createdAt,Instant updatedAt,
            ReportingCalendar.WeekWindow reportingWeek,ScheduleView currentSchedule,ScheduleView latestPendingSchedule) { }
    public record Page<T>(List<T> items,int page,int size,long totalElements) { }
    public record EventView(UUID id,LocalDate localDate,long sequence,String action,Instant acceptedAt,
            boolean completed,boolean restPlanned,String reason,UUID reportingWeekId) { }
    public record DayView(LocalDate localDate,boolean started,boolean scheduled,boolean completed,
            boolean restPlanned,boolean restApplicable,boolean eligible,String restReason,
            long version,ScheduleView schedule,ReportingCalendar.DayWindow reportingDay) { }
    public record History(UUID habitId,Instant cutoff,boolean exclusiveCutoff,Page<DayView> days,
            Page<EventView> actions,Page<ScheduleView> schedules) { }
    public record Consistency(int successfulDays,int eligibleDays,BigDecimal percentage,String reason) { }
}
