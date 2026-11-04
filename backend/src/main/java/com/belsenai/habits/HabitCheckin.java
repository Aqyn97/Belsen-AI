package com.belsenai.habits;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="habit_checkins")
public class HabitCheckin {
    @Id UUID id;
    @Column(nullable=false) UUID habitId;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) LocalDate localDate;
    @Column(nullable=false) UUID reportingWeekId;
    @Column(nullable=false) UUID scheduleVersionId;
    @Column(nullable=false) boolean completed;
    @Column(nullable=false) boolean restPlanned;
    @Version long version;
    @Column(nullable=false) long eventSequence;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    protected HabitCheckin() { }
    public UUID getId() { return id; }
    public UUID getHabitId() { return habitId; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getLocalDate() { return localDate; }
    public UUID getReportingWeekId() { return reportingWeekId; }
    public UUID getScheduleVersionId() { return scheduleVersionId; }
    public boolean getCompleted() { return completed; }
    public boolean getRestPlanned() { return restPlanned; }
    public long getVersion() { return version; }
    public long getEventSequence() { return eventSequence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
