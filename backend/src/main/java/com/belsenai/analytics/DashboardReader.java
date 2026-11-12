package com.belsenai.analytics;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import com.belsenai.goals.*;
import com.belsenai.habits.HabitRules;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Read projections only. Every query joins the dashboard's existing transaction. */
@Repository
@Transactional(readOnly=true,propagation=Propagation.MANDATORY)
public class DashboardReader {
    private final NamedParameterJdbcTemplate jdbc;
    public DashboardReader(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record TaskCounts(long scheduled,long completed) { }
    private MapSqlParameterSource parameters(UUID owner,ReportingCalendar.WeekWindow week,Instant bound) {
        return new MapSqlParameterSource("owner",owner).addValue("week",week.id(),java.sql.Types.OTHER)
                .addValue("from",week.weekStart()).addValue("to",week.weekStart().plusWeeks(1)).addValue("cutoff",Timestamp.from(bound));
    }
    public TaskCounts tasks(UUID owner,ReportingCalendar.WeekWindow week,Instant bound) {
        return jdbc.queryForObject("""
                SELECT count(*) AS scheduled, count(*) FILTER (WHERE state.status='COMPLETED') AS completed
                FROM tasks t
                CROSS JOIN LATERAL (
                    SELECT e.status,e.original_reporting_week_id FROM task_events e
                    WHERE e.task_id=t.id AND e.owner_id=:owner AND e.occurred_at<:cutoff
                    ORDER BY e.sequence DESC LIMIT 1
                ) state
                WHERE t.owner_id=:owner AND t.original_due_date>=:from AND t.original_due_date<:to
                  AND state.original_reporting_week_id=cast(:week AS uuid)
                """,parameters(owner,week,bound),(rs,row) -> new TaskCounts(rs.getLong("scheduled"),rs.getLong("completed")));
    }
    public List<HabitRules.DailyTotals> habits(UUID owner,List<ReportingCalendar.DayWindow> days,Instant bound) {
        if(days.isEmpty()) return List.of();
        var p=new MapSqlParameterSource("owner",owner).addValue("cutoff",Timestamp.from(bound));
        var values=new ArrayList<String>();
        for(int i=0;i<days.size();i++) {
            p.addValue("date"+i,days.get(i).localDate());p.addValue("bit"+i,1 << (days.get(i).localDate().getDayOfWeek().getValue()-1));
            values.add("(cast(:date"+i+" AS date),cast(:bit"+i+" AS integer))");
        }
        // Only internally generated parameter names enter SQL; all values remain bound.
        String sql="WITH days(local_date,weekday_bit) AS (VALUES "+String.join(",",values)+") "+"""
                SELECT d.local_date,
                       count(*) FILTER (WHERE NOT coalesce(state.rest_planned,false)) AS remaining,
                       count(*) FILTER (WHERE NOT coalesce(state.rest_planned,false) AND coalesce(state.completed,false)) AS completed
                FROM days d CROSS JOIN habits h
                CROSS JOIN LATERAL (
                    SELECT s.enabled,s.start_date,s.end_date,s.weekdays_mask
                    FROM habit_schedule_versions s WHERE s.habit_id=h.id AND s.owner_id=:owner
                      AND s.recorded_at<:cutoff AND s.effective_from<=d.local_date
                    ORDER BY s.sequence DESC LIMIT 1
                ) schedule
                LEFT JOIN habit_checkins c ON c.habit_id=h.id AND c.owner_id=:owner AND c.local_date=d.local_date
                LEFT JOIN LATERAL (
                    SELECT e.completed,e.rest_planned FROM habit_checkin_events e
                    WHERE e.checkin_id=c.id AND e.owner_id=:owner AND e.accepted_at<:cutoff
                    ORDER BY e.sequence DESC LIMIT 1
                ) state ON true
                WHERE h.owner_id=:owner AND h.created_at<:cutoff
                  AND (h.archived_at IS NULL OR h.archived_at>=:cutoff OR h.archive_effective_date>d.local_date)
                  AND schedule.enabled AND schedule.start_date<=d.local_date
                  AND (schedule.end_date IS NULL OR schedule.end_date>=d.local_date)
                  AND (cast(schedule.weekdays_mask AS integer) & d.weekday_bit)<>0
                GROUP BY d.local_date ORDER BY d.local_date
                """;
        return jdbc.query(sql,p,(rs,row) -> new HabitRules.DailyTotals(rs.getObject("local_date",LocalDate.class),rs.getLong("remaining"),rs.getLong("completed")));
    }
    private static final String RELEVANT="""
            g.owner_id=:owner AND g.created_at<:cutoff AND (
                g.archived_at IS NULL OR g.archived_at>=:cutoff
                OR EXISTS (SELECT 1 FROM goal_weekly_plans w WHERE w.goal_id=g.id AND w.owner_id=:owner AND w.reporting_week_id=cast(:week AS uuid) AND w.created_at<:cutoff)
                OR EXISTS (SELECT 1 FROM goal_progress_entries p WHERE p.goal_id=g.id AND p.owner_id=:owner AND p.reporting_week_id=cast(:week AS uuid)
                    AND p.effective_date>=:from AND p.effective_date<:to AND p.created_at<:cutoff))
            """;
    public DashboardDtos.Goals goals(UUID owner,ReportingCalendar.WeekWindow week,Instant bound,int page,int size) {
        var p=parameters(owner,week,bound).addValue("limit",size).addValue("offset",(long)page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM goals g WHERE "+RELEVANT,p,Long.class);
        var rows=jdbc.query("""
                WITH selected AS (SELECT g.* FROM goals g WHERE
                """+RELEVANT+" ORDER BY g.created_at,g.id LIMIT :limit OFFSET :offset) "+"""
                SELECT g.id,g.title,g.unit,w.planned_units,coalesce(actual.units,0) AS completed_units
                FROM selected g
                LEFT JOIN goal_weekly_plans w ON w.goal_id=g.id AND w.owner_id=:owner
                  AND w.reporting_week_id=cast(:week AS uuid) AND w.created_at<:cutoff
                LEFT JOIN LATERAL (
                    SELECT sum(p.quantity) AS units FROM goal_progress_entries p
                    WHERE p.goal_id=g.id AND p.owner_id=:owner AND p.reporting_week_id=cast(:week AS uuid)
                      AND p.effective_date>=:from AND p.effective_date<:to AND p.created_at<:cutoff
                      AND (p.voided_at IS NULL OR p.voided_at>=:cutoff)
                ) actual ON true
                ORDER BY g.created_at,g.id
                """,p,(rs,row) -> {
                    var units=rs.getBigDecimal("completed_units");var target=rs.getBigDecimal("planned_units");
                    return new DashboardDtos.Goal(rs.getObject("id",UUID.class),rs.getString("title"),"CURRENT",rs.getString("unit"),units,target,
                            target==null ? null : GoalRules.percent(units,target),target==null ? "NO_WEEKLY_TARGET" : null);
                });
        return new DashboardDtos.Goals(rows,page,size,total,total==0 ? "NO_GOALS" : null);
    }
}
