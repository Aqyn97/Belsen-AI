package com.belsenai.habits;

import java.time.*;
import java.util.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface HabitCheckinEventRepository extends Repository<HabitCheckinEvent, UUID> {
    HabitCheckinEvent save(HabitCheckinEvent value);
    Optional<HabitCheckinEvent> findByIdAndOwnerId(UUID id, UUID ownerId);
    @Query("select e from HabitCheckinEvent e where e.checkinId=:checkin and e.ownerId=:owner and (e.acceptedAt<:cutoff or (:inclusive=true and e.acceptedAt=:cutoff)) order by e.sequence desc")
    List<HabitCheckinEvent> state(UUID checkin,UUID owner,Instant cutoff,boolean inclusive,Pageable page);
    @Query("select e from HabitCheckinEvent e, HabitCheckin c where e.checkinId=c.id and e.ownerId=:owner and c.habitId=:habit and c.ownerId=:owner and c.localDate between :from and :to and (e.acceptedAt<:cutoff or (:inclusive=true and e.acceptedAt=:cutoff))")
    Page<HabitCheckinEvent> history(UUID habit,UUID owner,LocalDate from,LocalDate to,Instant cutoff,boolean inclusive,Pageable page);
}
