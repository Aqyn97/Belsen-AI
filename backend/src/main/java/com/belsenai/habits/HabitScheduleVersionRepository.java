package com.belsenai.habits;

import java.time.*;
import java.util.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface HabitScheduleVersionRepository extends Repository<HabitScheduleVersion, UUID> {
    HabitScheduleVersion save(HabitScheduleVersion value);
    Optional<HabitScheduleVersion> findByIdAndOwnerId(UUID id, UUID ownerId);
    @Query("select s from HabitScheduleVersion s where s.habitId=:habit and s.ownerId=:owner and s.effectiveFrom<=:date and (s.recordedAt<:cutoff or (:inclusive=true and s.recordedAt=:cutoff)) order by s.sequence desc")
    List<HabitScheduleVersion> effective(UUID habit,UUID owner,LocalDate date,Instant cutoff,boolean inclusive,Pageable page);
    @Query("select s from HabitScheduleVersion s where s.habitId=:habit and s.ownerId=:owner and s.effectiveFrom>:date and s.sequence>:currentSequence and s.recordedAt<=:cutoff order by s.sequence desc")
    List<HabitScheduleVersion> pending(UUID habit,UUID owner,LocalDate date,long currentSequence,Instant cutoff,Pageable page);
    @Query("select s from HabitScheduleVersion s where s.habitId=:habit and s.ownerId=:owner and (s.recordedAt<:cutoff or (:inclusive=true and s.recordedAt=:cutoff)) and s.effectiveFrom<=:to")
    Page<HabitScheduleVersion> history(UUID habit,UUID owner,LocalDate to,Instant cutoff,boolean inclusive,Pageable page);
}
