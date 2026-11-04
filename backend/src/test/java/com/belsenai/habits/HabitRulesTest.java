package com.belsenai.habits;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HabitRulesTest {
    @Test void everyIsoWeekdayHasADistinctBit() {
        var all=new ArrayList<Integer>();for(int i=1;i<=7;i++) { all.add(i);assertThat(HabitRules.mask(List.of(i))).isEqualTo((short)(1 << (i-1))); }
        assertThat(HabitRules.mask(all)).isEqualTo((short)127);
    }
    @Test void invalidAndDuplicateWeekdaysAreRejected() {
        for(var days:List.of(List.<Integer>of(),List.of(0),List.of(8),List.of(1,1))) assertThatThrownBy(() -> HabitRules.mask(days)).hasMessageContaining("weekdays");
        assertThatThrownBy(() -> HabitRules.mask(Arrays.asList(1,null))).hasMessageContaining("weekdays");
    }
    @Test void ifMatchRequiresOneStrongQuotedVersion() {
        assertThat(HabitRules.ifMatch("\"0\"")).isZero();assertThat(HabitRules.ifMatch("\"9223372036854775807\"")).isEqualTo(Long.MAX_VALUE);
        for(String bad:List.of("0","*","W/\"0\"","\"01\"","\"-1\"","\"0\",\"1\"","\"9223372036854775808\"")) assertThatThrownBy(() -> HabitRules.ifMatch(bad)).hasMessageContaining("If-Match");
        assertThatThrownBy(() -> HabitRules.ifMatch(null)).hasMessageContaining("If-Match");
    }
    @Test void historyIsBoundedAndDatesHaveSafeArithmeticRange() {
        LocalDate start=LocalDate.parse("2026-01-01");HabitRules.range(start,start.plusDays(92));
        assertThatThrownBy(() -> HabitRules.range(start,start.plusDays(93))).hasMessageContaining("93");
        assertThatThrownBy(() -> HabitRules.dates(start,start.minusDays(1))).hasMessageContaining("endDate");
        assertThatThrownBy(() -> HabitRules.validDate(LocalDate.MAX)).hasMessageContaining("Dates");
    }
    HabitRules.Obligation row(UUID habit,int day,boolean scheduled,boolean rest,boolean completed) {
        return new HabitRules.Obligation(habit,LocalDate.of(2026,11,day),day<=4,scheduled,rest,completed);
    }
    @Test void allHabitsMustBeCompleteForOneSuccessfulDay() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
        var score=HabitRules.consistency(List.of(row(a,2,true,false,true),row(b,2,true,false,true),row(c,2,true,false,false)));
        assertThat(score.eligibleDays()).isEqualTo(1);assertThat(score.successfulDays()).isZero();assertThat(score.percentage()).isEqualByComparingTo("0");
    }
    @Test void restRemovesOnlyItsHabitAndFutureDaysAreExcluded() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();
        var score=HabitRules.consistency(List.of(row(a,2,true,false,true),row(b,2,true,true,false),row(a,5,true,false,false)));
        assertThat(score.eligibleDays()).isEqualTo(1);assertThat(score.successfulDays()).isEqualTo(1);
    }
    @Test void allRestAndUnscheduledDaysProduceNoData() {
        var a=UUID.randomUUID();var score=HabitRules.consistency(List.of(row(a,2,true,true,false),row(a,3,false,false,false)));
        assertThat(score.eligibleDays()).isZero();assertThat(score.percentage()).isNull();assertThat(score.reason()).isEqualTo("NO_SCHEDULED_ACTIVITY");
    }
    @Test void displayPercentageRoundsButPreservesIntegerCounts() {
        var a=UUID.randomUUID();var score=HabitRules.consistency(List.of(row(a,2,true,false,true),row(a,3,true,false,true),row(a,4,true,false,false)));
        assertThat(score.successfulDays()).isEqualTo(2);assertThat(score.eligibleDays()).isEqualTo(3);assertThat(score.percentage()).isEqualByComparingTo("66.7");
    }
    @Test void duplicateHabitDateEvidenceIsRejected() {
        var row=row(UUID.randomUUID(),2,true,false,true);
        assertThatThrownBy(() -> HabitRules.consistency(List.of(row,row))).hasMessageContaining("Duplicate");
    }
}
