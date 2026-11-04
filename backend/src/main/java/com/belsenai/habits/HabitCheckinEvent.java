package com.belsenai.habits;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="habit_checkin_events")
@org.hibernate.annotations.Immutable
public class HabitCheckinEvent {
    @Id UUID id;
    @Column(nullable=false) UUID checkinId;
    @Column(nullable=false) UUID habitId;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) long sequence;
    @Column(nullable=false, length=30) String action;
    @Column(nullable=false) Instant acceptedAt;
    @Column(nullable=false) boolean completed;
    @Column(nullable=false) boolean restPlanned;
    @Column(length=500) String reason;
    @Column(nullable=false) UUID reportingWeekId;
    protected HabitCheckinEvent() { }
    public UUID getId() { return id; }
    public UUID getCheckinId() { return checkinId; }
    public UUID getHabitId() { return habitId; }
    public UUID getOwnerId() { return ownerId; }
    public long getSequence() { return sequence; }
    public String getAction() { return action; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public boolean getCompleted() { return completed; }
    public boolean getRestPlanned() { return restPlanned; }
    public String getReason() { return reason; }
    public UUID getReportingWeekId() { return reportingWeekId; }
}
