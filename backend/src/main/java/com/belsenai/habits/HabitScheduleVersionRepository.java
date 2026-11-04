package com.belsenai.habits;

import java.util.*;
import org.springframework.data.repository.Repository;

public interface HabitScheduleVersionRepository extends Repository<HabitScheduleVersion, UUID> {
    HabitScheduleVersion save(HabitScheduleVersion value);
    Optional<HabitScheduleVersion> findByIdAndOwnerId(UUID id, UUID ownerId);
}
