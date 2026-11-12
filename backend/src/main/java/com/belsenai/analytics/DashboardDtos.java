package com.belsenai.analytics;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.belsenai.habits.HabitDtos;

public final class DashboardDtos {
    private DashboardDtos() { }
    public record Period(LocalDate weekStart,String timezone,Instant startsAt,Instant endsAt,
            Instant cutoff,boolean cutoffExclusive,String calendarStatus,String reportStatus,int calculationVersion) { }
    public record Tasks(long scheduled,long completed,long incomplete,BigDecimal percentage,String reason) { }
    public record Goal(UUID goalId,String title,String titleMetadata,String unit,BigDecimal completedUnits,
            BigDecimal plannedUnits,BigDecimal percentage,String reason) { }
    public record Goals(List<Goal> items,int page,int size,long totalElements,String reason) { }
    public record Dashboard(Period period,Tasks tasks,HabitDtos.Consistency habits,Goals goals) { }
}
