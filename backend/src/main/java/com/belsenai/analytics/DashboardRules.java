package com.belsenai.analytics;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import com.belsenai.common.error.ApiException;

public final class DashboardRules {
    private DashboardRules() { }
    public static void week(LocalDate date) {
        if(date.getYear()<1900 || date.getYear()>9998 || date.getDayOfWeek()!=DayOfWeek.MONDAY) throw new ApiException(400,"weekStart must be a Monday between years 1900 and 9998.");
    }
    public static void page(int page,int size) {
        if(page<0 || size<1 || size>100) throw new ApiException(400,"Use goalsPage >= 0 and goalsSize between 1 and 100.");
    }
    public static Instant cutoff(Instant requested,Instant now,Instant start,Instant end) {
        if(!end.isAfter(start)) throw new ApiException(409,"Invalid reporting interval.");
        if(now.isBefore(start)) throw new ApiException(400,"Future reporting weeks are unavailable.");
        Instant cutoff=requested==null ? (now.isBefore(end) ? now : end) : requested;
        if(cutoff.isBefore(start) || (requested!=null && cutoff.equals(start)) || cutoff.isAfter(end) || cutoff.isAfter(now)) throw new ApiException(400,"cutoff must be after period start, at or before period end, and not in the future.");
        return cutoff;
    }
    /** Preserve exclusive comparisons against PostgreSQL's microsecond ticks. */
    public static Instant sqlBound(Instant cutoff) {
        Instant tick=cutoff.truncatedTo(ChronoUnit.MICROS);return tick.equals(cutoff) ? tick : tick.plusNanos(1000);
    }
    public static BigDecimal percent(long completed,long total) {
        return total==0 ? null : BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);
    }
}
