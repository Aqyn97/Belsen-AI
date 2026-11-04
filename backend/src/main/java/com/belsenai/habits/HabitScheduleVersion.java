package com.belsenai.habits;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="habit_schedule_versions")
@org.hibernate.annotations.Immutable
public class HabitScheduleVersion {
    @Id UUID id;
    @Column(nullable=false) UUID habitId;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) long sequence;
    @Column(nullable=false) LocalDate effectiveFrom;
    @Column(nullable=false) LocalDate startDate;
    LocalDate endDate;
    @Column(nullable=false) short weekdaysMask;
    @Column(nullable=false) boolean enabled;
    @Column(nullable=false) Instant recordedAt;
    @Column(nullable=false) UUID reportingWeekId;
    protected HabitScheduleVersion() { }
    public UUID getId() { return id; }
    public UUID getHabitId() { return habitId; }
    public UUID getOwnerId() { return ownerId; }
    public long getSequence() { return sequence; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public short getWeekdaysMask() { return weekdaysMask; }
    public boolean getEnabled() { return enabled; }
    public Instant getRecordedAt() { return recordedAt; }
    public UUID getReportingWeekId() { return reportingWeekId; }
}
