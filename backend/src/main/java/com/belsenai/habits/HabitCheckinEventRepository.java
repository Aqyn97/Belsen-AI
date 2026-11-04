package com.belsenai.habits;

import java.util.*;
import org.springframework.data.repository.Repository;

public interface HabitCheckinEventRepository extends Repository<HabitCheckinEvent, UUID> {
    HabitCheckinEvent save(HabitCheckinEvent value);
    Optional<HabitCheckinEvent> findByIdAndOwnerId(UUID id, UUID ownerId);
}
