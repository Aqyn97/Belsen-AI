package com.belsenai.analytics;

import java.time.*;
import java.util.*;
import com.belsenai.habits.HabitRules;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DashboardRulesTest {
    final Instant start=Instant.parse("2026-11-01T19:00:00Z"),end=Instant.parse("2026-11-08T19:00:00Z");
    @Test void defaultCutoffUsesNowOrExclusiveWeekEnd() {
        assertThat(DashboardRules.cutoff(null,start,start,end)).isEqualTo(start);
        assertThat(DashboardRules.cutoff(null,start.plusSeconds(1),start,end)).isEqualTo(start.plusSeconds(1));
        assertThat(DashboardRules.cutoff(null,end.plusSeconds(1),start,end)).isEqualTo(end);
    }
    @Test void explicitCutoffBoundsAreValidated() {
        assertThat(DashboardRules.cutoff(end,end.plusSeconds(1),start,end)).isEqualTo(end);
        for(Instant cutoff:List.of(start,start.minusSeconds(1),end.plusSeconds(1))) assertThatThrownBy(() -> DashboardRules.cutoff(cutoff,end,start,end)).hasMessageContaining("cutoff");
        assertThatThrownBy(() -> DashboardRules.cutoff(end,start.plusSeconds(1),start,end)).hasMessageContaining("cutoff");
    }
    @Test void invalidAndFutureIntervalsAreRejected() {
        assertThatThrownBy(() -> DashboardRules.cutoff(null,start.minusNanos(1),start,end)).hasMessageContaining("Future");
        assertThatThrownBy(() -> DashboardRules.cutoff(null,end,end,start)).hasMessageContaining("interval");
    }
    @Test void weekAndPaginationAreBounded() {
        DashboardRules.week(LocalDate.parse("2026-11-02"));DashboardRules.page(0,100);
        assertThatThrownBy(() -> DashboardRules.week(LocalDate.parse("2026-11-03"))).hasMessageContaining("Monday");
        assertThatThrownBy(() -> DashboardRules.week(LocalDate.MIN)).hasMessageContaining("Monday");
        assertThatThrownBy(() -> DashboardRules.page(-1,20)).hasMessageContaining("goalsPage");
        assertThatThrownBy(() -> DashboardRules.page(0,101)).hasMessageContaining("goalsSize");
    }
    @Test void exclusiveSqlBoundCeilsWithoutRoundingAnExactTick() {
        assertThat(DashboardRules.sqlBound(start)).isEqualTo(start);
        assertThat(DashboardRules.sqlBound(start.plusNanos(1))).isEqualTo(start.plusNanos(1000));
        assertThat(DashboardRules.sqlBound(start.plusNanos(999))).isEqualTo(start.plusNanos(1000));
    }
    @Test void taskPercentKeepsCountsAndDoesNotInventNoData() {
        assertThat(DashboardRules.percent(0,0)).isNull();assertThat(DashboardRules.percent(2,3)).isEqualByComparingTo("66.7");
    }
    @Test void dailyAggregatesReuseStrictConsistencyWithoutAHabitRowLimit() {
        var result=HabitRules.consistencyFromDays(List.of(new HabitRules.DailyTotals(LocalDate.parse("2026-11-02"),20000,19999),
                new HabitRules.DailyTotals(LocalDate.parse("2026-11-03"),20000,20000),new HabitRules.DailyTotals(LocalDate.parse("2026-11-04"),0,0)));
        assertThat(result.eligibleDays()).isEqualTo(2);assertThat(result.successfulDays()).isEqualTo(1);assertThat(result.percentage()).isEqualByComparingTo("50");
    }
    @Test void inconsistentDailyAggregatesCannotProduceFabricatedScores() {
        for(var day:List.of(new HabitRules.DailyTotals(LocalDate.now(),1,2),new HabitRules.DailyTotals(LocalDate.now(),-1,0)))
            assertThatThrownBy(() -> HabitRules.consistencyFromDays(List.of(day))).hasMessageContaining("Invalid");
    }
}
