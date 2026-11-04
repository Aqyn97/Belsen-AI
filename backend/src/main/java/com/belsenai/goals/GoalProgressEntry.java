package com.belsenai.goals;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="goal_progress_entries")
public class GoalProgressEntry {
    @Id UUID id;
    @Column(nullable=false) UUID goalId;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) UUID reportingWeekId;
    @Column(nullable=false) UUID submissionId;
    @Column(nullable=false, precision=19, scale=4) BigDecimal quantity;
    @Column(nullable=false) LocalDate effectiveDate;
    @Column(nullable=false) Instant createdAt;
    Instant voidedAt;
    protected GoalProgressEntry() { }
}
