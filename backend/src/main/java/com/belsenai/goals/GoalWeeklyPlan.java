package com.belsenai.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="goal_weekly_plans")
public class GoalWeeklyPlan {
    @Id UUID id;
    @Column(nullable=false) UUID goalId;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false) UUID reportingWeekId;
    @Column(nullable=false, precision=19, scale=4) BigDecimal plannedUnits;
    @Version long version;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    protected GoalWeeklyPlan() { }
}
