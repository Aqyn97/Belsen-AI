package com.belsenai.habits;

import java.util.*;
import org.springframework.data.repository.Repository;

public interface HabitCheckinRepository extends Repository<HabitCheckin, UUID> {
    HabitCheckin save(HabitCheckin value);
    Optional<HabitCheckin> findByIdAndOwnerId(UUID id, UUID ownerId);
}
