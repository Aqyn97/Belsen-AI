package com.belsenai.habits;

import java.util.*;
import org.springframework.data.repository.Repository;

public interface HabitRepository extends Repository<Habit, UUID> {
    Habit save(Habit value);
    Optional<Habit> findByIdAndOwnerId(UUID id, UUID ownerId);
}
