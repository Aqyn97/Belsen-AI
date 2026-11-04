package com.belsenai.goals;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="reporting_weeks")
public class ReportingWeek {
    @Id UUID id;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) LocalDate weekStart;
    @Column(nullable=false, length=100) String timezone;
    @Column(nullable=false) Instant startsAt;
    @Column(nullable=false) Instant endsAt;
    public java.util.UUID getId() { return id; }
    public java.time.LocalDate getWeekStart() { return weekStart; }
    public String getTimezone() { return timezone; }
    public java.time.Instant getStartsAt() { return startsAt; }
    public java.time.Instant getEndsAt() { return endsAt; }
    protected ReportingWeek() { }
}
