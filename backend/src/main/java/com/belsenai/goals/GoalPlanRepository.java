package com.belsenai.goals;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalPlanRepository extends JpaRepository<GoalWeeklyPlan, UUID> {
    Optional<GoalWeeklyPlan> findByGoalIdAndReportingWeekId(UUID goalId, UUID reportingWeekId);
    List<GoalWeeklyPlan> findByGoalId(UUID goalId);
    boolean existsByGoalId(UUID goalId);
}
