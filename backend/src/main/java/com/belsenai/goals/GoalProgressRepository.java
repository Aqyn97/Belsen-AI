package com.belsenai.goals;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface GoalProgressRepository extends JpaRepository<GoalProgressEntry, UUID> {
    Optional<GoalProgressEntry> findByGoalIdAndSubmissionId(UUID goalId, UUID submissionId);
    Optional<GoalProgressEntry> findByIdAndGoalIdAndOwnerId(UUID id, UUID goalId, UUID ownerId);
    Page<GoalProgressEntry> findByGoalIdAndOwnerId(UUID goalId, UUID ownerId, Pageable pageable);
    boolean existsByGoalId(UUID goalId);
    @Query("select coalesce(sum(p.quantity),0) from GoalProgressEntry p where p.goalId=:goalId and p.voidedAt is null")
    BigDecimal total(UUID goalId);
    @Query("select coalesce(sum(p.quantity),0) from GoalProgressEntry p where p.goalId=:goalId and p.reportingWeekId=:weekId and p.voidedAt is null")
    BigDecimal weeklyTotal(UUID goalId, UUID weekId);
}
