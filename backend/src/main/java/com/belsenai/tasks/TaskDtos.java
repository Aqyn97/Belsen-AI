package com.belsenai.tasks;

import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;

public final class TaskDtos {
    private TaskDtos() { }
    public record Create(@NotBlank @Size(max=200) String title, @Size(max=2000) String description,
            UUID goalId, LocalDate dueDate, @Positive @Max(1440) Integer estimatedDurationMinutes) { }
    public record Version(@NotNull @PositiveOrZero Long version) { }
    public record Reschedule(@NotNull @PositiveOrZero Long version,
            @JsonProperty(required=true) LocalDate dueDate) { }
    public static class Update {
        @JsonProperty @NotNull @PositiveOrZero private Long version;
        @JsonSetter(nulls=Nulls.FAIL) @Size(min=1,max=200) private String title;
        @Size(max=2000) private String description;
        private UUID goalId;
        @Positive @Max(1440) private Integer estimatedDurationMinutes;
        private boolean descriptionProvided;
        private boolean goalProvided;
        private boolean durationProvided;
        @JsonSetter("description") public void setDescription(String value) { description=value; descriptionProvided=true; }
        @JsonSetter("goalId") public void setGoalId(UUID value) { goalId=value; goalProvided=true; }
        @JsonSetter("estimatedDurationMinutes") public void setDuration(Integer value) { estimatedDurationMinutes=value; durationProvided=true; }
        public Long version() { return version; }
        public String title() { return title; }
        public String description() { return description; }
        public UUID goalId() { return goalId; }
        public Integer duration() { return estimatedDurationMinutes; }
        public boolean descriptionProvided() { return descriptionProvided; }
        public boolean goalProvided() { return goalProvided; }
        public boolean durationProvided() { return durationProvided; }
    }
    public record WeekView(UUID id, LocalDate weekStart, String timezone, Instant startsAt, Instant endsAt) { }
    public record View(UUID id, String title, String description, UUID goalId,
            LocalDate originalDueDate, LocalDate currentDueDate, WeekView originalReportingWeek,
            Integer estimatedDurationMinutes, TaskStatus status, Instant completedAt, Instant archivedAt,
            Instant createdAt, Instant updatedAt, long version, long eventSequence) { }
    public record PageView(List<View> items,int page,int size,long totalElements) { }
}
