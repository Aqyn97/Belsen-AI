package com.belsenai.goals;

import java.math.BigDecimal;
import java.time.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GoalRulesTest {
    @Test void numericRulesRejectExcessScaleAndPrecision() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(String value:new String[]{"0","-1","0.00001","1000000000000000"}) {
                assertThat(validator.validate(new GoalDtos.Create("Goal",null,new BigDecimal(value),"units",null,null))).isNotEmpty();
            }
            assertThat(validator.validate(new GoalDtos.Create("Goal",null,new BigDecimal("0.0001"),"units",null,null))).isEmpty();
        }
    }
    @Test void percentagesCapOnlyPercentage() {
        assertThat(GoalRules.percent(new BigDecimal("12"),new BigDecimal("20"))).isEqualByComparingTo("60");
        assertThat(GoalRules.percent(new BigDecimal("27"),new BigDecimal("20"))).isEqualByComparingTo("100");
        assertThat(GoalRules.percent(BigDecimal.ZERO,BigDecimal.ONE)).isEqualByComparingTo("0");
    }
    @Test void calendarUsesMondayAndTimezoneRules() {
        assertThat(ReportingCalendar.monday(LocalDate.parse("2026-11-08"))).isEqualTo(LocalDate.parse("2026-11-02"));
        ZoneId zone=ZoneId.of("America/New_York");LocalDate start=LocalDate.parse("2026-10-26");
        assertThat(Duration.between(start.atStartOfDay(zone),start.plusWeeks(1).atStartOfDay(zone)).toHours()).isEqualTo(169);
    }
    @Test void datesRejectReversedRange() {
        assertThatThrownBy(() -> GoalRules.dates(LocalDate.parse("2026-11-03"),LocalDate.parse("2026-11-02"))).hasMessageContaining("Deadline");
    }
}
