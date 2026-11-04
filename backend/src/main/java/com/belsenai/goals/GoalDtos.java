package com.belsenai.goals;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;

public final class GoalDtos {
    private GoalDtos() { }
    public record Create(@NotBlank @Size(max=200) String title, @Size(max=2000) String description,
            @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=15,fraction=4) BigDecimal target,
            @NotBlank @Size(max=50) String unit, LocalDate startDate, LocalDate deadline) { }
    public static class Update {
        @com.fasterxml.jackson.annotation.JsonProperty
        @NotNull @PositiveOrZero private Long version;
        @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL)
        @Size(min=1,max=200) private String title;
        @Size(max=2000) private String description;
        @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL)
        @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) private BigDecimal target;
        @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL)
        @Size(min=1,max=50) private String unit;
        private LocalDate startDate;
        private LocalDate deadline;
        private boolean clearDescription;
        private boolean clearStartDate;
        private boolean clearDeadline;
        @com.fasterxml.jackson.annotation.JsonSetter("description")
        public void setDescription(String value) { description=value; clearDescription=value==null; }
        @com.fasterxml.jackson.annotation.JsonSetter("startDate")
        public void setStartDate(LocalDate value) { startDate=value; clearStartDate=value==null; }
        @com.fasterxml.jackson.annotation.JsonSetter("deadline")
        public void setDeadline(LocalDate value) { deadline=value; clearDeadline=value==null; }
        public Long version() { return version; }
        public String title() { return title; }
        public String description() { return description; }
        public BigDecimal target() { return target; }
        public String unit() { return unit; }
        public LocalDate startDate() { return startDate; }
        public LocalDate deadline() { return deadline; }
        public Boolean clearDescription() { return clearDescription; }
        public Boolean clearStartDate() { return clearStartDate; }
        public Boolean clearDeadline() { return clearDeadline; }
    }
    public record Version(@NotNull @PositiveOrZero Long version) { }
    public record Progress(@NotNull UUID submissionId,
            @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal quantity,
            @NotNull LocalDate effectiveDate) { }
    public record Plan(@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal plannedUnits,
            @PositiveOrZero Long version) { }
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) { }
    public record WeekView(LocalDate weekStart, String timezone, Instant startsAt, Instant endsAt) { }
    public record PlanView(UUID id, WeekView reportingWeek, BigDecimal plannedUnits, BigDecimal completedUnits,
            BigDecimal weeklyPercent, long version) { }
    public record View(UUID id, String title, String description, BigDecimal target, String unit,
            LocalDate startDate, LocalDate deadline, String status, Instant archivedAt, long version,
            BigDecimal lifetimeUnits, BigDecimal lifetimePercent, List<PlanView> weeklyPlans) { }
    public record ProgressView(UUID id, UUID submissionId, BigDecimal quantity, LocalDate effectiveDate,
            Instant createdAt, Instant voidedAt, WeekView reportingWeek) { }
    public record Recorded(ProgressView entry, boolean created) { }
}
