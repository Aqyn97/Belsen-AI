package com.belsenai.goals;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="reporting_timezone_revisions")
@org.hibernate.annotations.Immutable
public class ReportingTimezoneRevision {
    @Id UUID id;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) long sequence;
    @Column(nullable=false) LocalDate effectiveWeekStart;
    @Column(nullable=false, length=100) String timezone;
    @Column(nullable=false) Instant recordedAt;
    protected ReportingTimezoneRevision() { }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public long getSequence() { return sequence; }
    public LocalDate getEffectiveWeekStart() { return effectiveWeekStart; }
    public String getTimezone() { return timezone; }
    public Instant getRecordedAt() { return recordedAt; }
}
