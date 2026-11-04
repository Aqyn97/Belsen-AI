package com.belsenai.goals;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportingWeekRepository extends JpaRepository<ReportingWeek, UUID> {
    Optional<ReportingWeek> findByOwnerIdAndWeekStart(UUID ownerId, LocalDate weekStart);
    List<ReportingWeek> findByOwnerIdOrderByWeekStartAsc(UUID ownerId);
}
