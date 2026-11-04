package com.belsenai.goals;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Optional<Goal> findByIdAndOwnerId(UUID id, UUID ownerId);
    Page<Goal> findByOwnerId(UUID ownerId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goal g where g.id = :id and g.ownerId = :ownerId")
    Optional<Goal> lockOwned(UUID id, UUID ownerId);
}
