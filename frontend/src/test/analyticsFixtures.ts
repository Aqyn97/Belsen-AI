import { exact } from '../shared/lib/numbers';
export function reportFixture() {
  return { period: { weekStart:'2026-11-02', timezone:'America/New_York', startsAt:'2026-11-02T05:00:00Z', endsAt:'2026-11-09T05:00:00Z', cutoff:'2026-11-02T12:00:00.123456Z', cutoffExclusive:true, calendarStatus:'FROZEN', reportStatus:'PROVISIONAL', calculationVersion:1 },
    tasks:{scheduled:3,completed:1,incomplete:2,percentage:exact('33.3') as ReturnType<typeof exact>|null,reason:null as string|null},
    habits:{successfulDays:1,eligibleDays:2,percentage:exact('50.0') as ReturnType<typeof exact>|null,reason:null as string|null},
    goals:{items:[{goalId:'22222222-2222-4222-8222-222222222222',title:'Read thoughtfully',titleMetadata:'CURRENT',unit:'pages',completedUnits:exact('12.3456'),plannedUnits:exact('20.0000') as ReturnType<typeof exact>|null,percentage:exact('61.7') as ReturnType<typeof exact>|null,reason:null as string|null}],page:0,size:5,totalElements:1,reason:null as string|null} };
}
export function emptyReportFixture() {
  const r=reportFixture();r.period.calendarStatus='UNFROZEN';r.tasks={scheduled:0,completed:0,incomplete:0,percentage:null,reason:'NO_SCHEDULED_TASKS'};r.habits={successfulDays:0,eligibleDays:0,percentage:null,reason:'NO_SCHEDULED_ACTIVITY'};r.goals={items:[],page:0,size:5,totalElements:0,reason:'NO_GOALS'};return r;
}
