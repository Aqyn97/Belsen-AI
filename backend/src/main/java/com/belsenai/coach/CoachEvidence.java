package com.belsenai.coach;

import com.belsenai.analytics.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Only this whitelist may become provider input. No resource metadata is copied. */
@Service
public class CoachEvidence {
    public enum Action { REVIEW_TASKS, REVIEW_ROUTINE, PLAN_TARGET, NEXT_GOAL_STEP, REVIEW_NEXT_PLAN }
    public enum Destination { TASKS, HABITS, GOALS }
    public record Candidate(String id,int order,Action action,Destination destination,String condition,
            String explanationTemplate,List<String> evidenceIds) {
        public Candidate { evidenceIds=List.copyOf(evidenceIds); }
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String title() { return switch(action) {
            case REVIEW_TASKS -> "Review task commitments";
            case REVIEW_ROUTINE -> "Review routine feasibility";
            case PLAN_TARGET -> "Make goal progress measurable";
            case NEXT_GOAL_STEP -> "Choose a next goal step";
            case REVIEW_NEXT_PLAN -> "Review the next plan";
        }; }
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String nextAction() { return switch(destination) {
            case TASKS -> "Review current tasks and choose a feasible commitment.";
            case HABITS -> "Review your habits and consider a manageable future schedule.";
            case GOALS -> "Review your goals and the next open planning period.";
        }; }
    }
    public record Fact(String id,String actual,String target,String percentage,String reason) { }
    public record Coverage(int includedGoals,long totalGoals,boolean truncated) { }
    public record Snapshot(int schemaVersion,DashboardDtos.Period period,List<Fact> facts,
            List<Candidate> candidates,Coverage coverage) {
        public Snapshot { facts=List.copyOf(facts);candidates=List.copyOf(candidates); }
    }
    private final DashboardService dashboard;
    public CoachEvidence(DashboardService dashboard) { this.dashboard=dashboard; }
    public Snapshot prepare(UUID owner,LocalDate week,Instant cutoff) {
        return from(dashboard.dashboard(owner,week,cutoff,0,20));
    }
    public static Snapshot from(DashboardDtos.Dashboard d) {
        var facts=new ArrayList<Fact>();var candidates=new ArrayList<Candidate>();
        facts.add(new Fact("tasks",Long.toString(d.tasks().completed()),Long.toString(d.tasks().scheduled()),decimal(d.tasks().percentage()),d.tasks().reason()));
        facts.add(new Fact("habits",Integer.toString(d.habits().successfulDays()),Integer.toString(d.habits().eligibleDays()),decimal(d.habits().percentage()),d.habits().reason()));
        if(d.tasks().incomplete()>0) add(candidates,"tasks",Action.REVIEW_TASKS,Destination.TASKS,"INCOMPLETE_TASKS","Review current commitments; original reporting obligations remain.");
        if(d.habits().eligibleDays()>d.habits().successfulDays()) add(candidates,"habits",Action.REVIEW_ROUTINE,Destination.HABITS,"INCOMPLETE_ELIGIBLE_DAYS","Review future routine feasibility; today's obligations may still be in progress.");
        int index=0;
        for(var g:d.goals().items()) {
            String id="g"+(++index);
            facts.add(new Fact(id,decimal(g.completedUnits()),decimal(g.plannedUnits()),decimal(g.percentage()),g.reason()));
            if(g.plannedUnits()==null) add(candidates,id,Action.PLAN_TARGET,Destination.GOALS,"NO_WEEKLY_TARGET","Review weekly planning; recorded units have no target percentage.");
            else if(g.completedUnits().compareTo(g.plannedUnits())<0) add(candidates,id,Action.NEXT_GOAL_STEP,Destination.GOALS,"BELOW_WEEKLY_TARGET","Review this goal and choose a feasible next action.");
            else add(candidates,id,Action.REVIEW_NEXT_PLAN,Destination.GOALS,"TARGET_MET","Review the next open planning period; preserve actual over-target units.");
        }
        return new Snapshot(1,d.period(),facts,candidates,new Coverage(index,d.goals().totalElements(),d.goals().totalElements()>index));
    }
    private static void add(List<Candidate> list,String evidence,Action action,Destination destination,String condition,String explanation) {
        int order=list.size();list.add(new Candidate("c"+(order+1),order,action,destination,condition,explanation,List.of(evidence)));
    }
    private static String decimal(java.math.BigDecimal value) { return value==null ? null : value.toPlainString(); }
}
