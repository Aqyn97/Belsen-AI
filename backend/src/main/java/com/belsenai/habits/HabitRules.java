package com.belsenai.habits;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.*;
import com.belsenai.common.error.ApiException;

public final class HabitRules {
    private HabitRules() { }
    public static short mask(List<Integer> days) {
        if(days==null || days.isEmpty() || days.size()>7) throw new ApiException(400,"Choose one to seven distinct ISO weekdays.");
        int mask=0;
        for(Integer d:days) {
            if(d==null || d<1 || d>7 || (mask & (1 << (d-1)))!=0) throw new ApiException(400,"Choose one to seven distinct ISO weekdays.");
            mask |= 1 << (d-1);
        }
        return (short)mask;
    }
    static List<Integer> weekdays(short mask) {
        var result=new ArrayList<Integer>();for(int d=1;d<=7;d++) if((mask & (1 << (d-1)))!=0) result.add(d);return result;
    }
    public static void dates(LocalDate start,LocalDate end) {
        validDate(start);if(end!=null) { validDate(end);if(end.isBefore(start)) throw new ApiException(400,"endDate must not precede startDate."); }
    }
    public static void validDate(LocalDate date) {
        if(date==null || date.getYear()<1900 || date.getYear()>9998) throw new ApiException(400,"Dates must be between years 1900 and 9998.");
    }
    public static void range(LocalDate from,LocalDate to) {
        validDate(from);validDate(to);
        if(to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>=93) throw new ApiException(400,"History requires an ordered range of at most 93 days.");
    }
    public static void page(int page,int size) {
        if(page<0 || size<1 || size>100) throw new ApiException(400,"Use page >= 0 and size between 1 and 100.");
    }
    public static long ifMatch(String value) {
        if(value==null || !value.matches("\"(0|[1-9][0-9]*)\"")) throw new ApiException(400,"If-Match must contain one quoted nonnegative version.");
        try { return Long.parseLong(value.substring(1,value.length()-1)); }
        catch(NumberFormatException invalid) { throw new ApiException(400,"If-Match version is out of range."); }
    }
    public record Obligation(UUID habitId,LocalDate date,boolean started,boolean scheduled,boolean restPlanned,boolean completed) { }
    public static HabitDtos.Consistency consistency(List<Obligation> rows) {
        if(rows.size()>10000) throw new ApiException(400,"Consistency input is too large.");
        var keys=new HashSet<String>();var days=new HashMap<LocalDate,List<Obligation>>();
        for(var row:rows) {
            if(!keys.add(row.habitId()+"/"+row.date())) throw new ApiException(400,"Duplicate habit/date evidence.");
            if(row.started() && row.scheduled() && !row.restPlanned()) days.computeIfAbsent(row.date(),d -> new ArrayList<>()).add(row);
        }
        return consistencyFromDays(days.entrySet().stream().map(day -> new DailyTotals(day.getKey(),day.getValue().size(),day.getValue().stream().filter(Obligation::completed).count())).toList());
    }
    public record DailyTotals(LocalDate date,long remaining,long completed) { }
    public static HabitDtos.Consistency consistencyFromDays(List<DailyTotals> days) {
        if(days.size()>10000) throw new ApiException(400,"Consistency input is too large.");
        var dates=new HashSet<LocalDate>();int eligible=0,successful=0;
        for(var day:days) {
            if(!dates.add(day.date()) || day.remaining()<0 || day.completed()<0 || day.completed()>day.remaining()) throw new ApiException(400,"Invalid daily consistency counts.");
            if(day.remaining()>0) { eligible++;if(day.completed()==day.remaining()) successful++; }
        }
        return new HabitDtos.Consistency(successful,eligible,eligible==0 ? null : BigDecimal.valueOf(successful).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(eligible),1,RoundingMode.HALF_UP),eligible==0 ? "NO_SCHEDULED_ACTIVITY" : null);
    }
}
