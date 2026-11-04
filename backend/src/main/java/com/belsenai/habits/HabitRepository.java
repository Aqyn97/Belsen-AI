package com.belsenai.habits;

import java.util.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface HabitRepository extends Repository<Habit, UUID> {
    Habit save(Habit value);
    void flush();
    Optional<Habit> findByIdAndOwnerId(UUID id, UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Habit h where h.id=:id and h.ownerId=:owner")
    Optional<Habit> lockOwned(UUID id,UUID owner);
    Page<Habit> findByOwnerIdAndArchivedAtIsNull(UUID owner,Pageable page);
    Page<Habit> findByOwnerIdAndArchivedAtIsNotNull(UUID owner,Pageable page);
}
