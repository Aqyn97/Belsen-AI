package com.belsenai.goals;

import java.math.*;
import java.time.LocalDate;
import com.belsenai.common.error.ApiException;

public final class GoalRules {
    private GoalRules() { }
    public static void dates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) { throw new ApiException(400,"Deadline precedes start date."); }
    }
    public static BigDecimal percent(BigDecimal actual, BigDecimal planned) {
        return actual.multiply(BigDecimal.valueOf(100)).divide(planned, 1, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
    }
}
