import { numeric, object, text, week, pageOf, type Week, type Page } from '../../shared/product/contracts';
import { validDate } from '../../shared/product/forms';
export interface ReportPeriod extends Week {
  cutoff: string;
  cutoffExclusive: true;
  calendarStatus: 'FROZEN' | 'RECONSTRUCTED' | 'UNFROZEN';
  reportStatus: 'PROVISIONAL' | 'CLOSED_UNFINALIZED';
  calculationVersion: string;
}
export interface GoalMetric {
  goalId: string; title: string; titleMetadata: 'CURRENT'; unit: string;
  completedUnits: string; plannedUnits: string | null; percentage: string | null; reason: string | null;
}
export interface Dashboard {
  period: ReportPeriod;
  tasks: { scheduled: string; completed: string; incomplete: string; percentage: string | null; reason: string | null };
  habits: { successfulDays: string; eligibleDays: string; percentage: string | null; reason: string | null };
  goals: Page<GoalMetric> & { reason: string | null };
}
function choice<T extends string>(v: unknown, values: T[]): T { if (!values.includes(v as T)) throw new Error('Unsupported reporting state.'); return v as T; }
const nullableText = (v: unknown) => v === null ? null : text(v);
function count(v: unknown) { const s = numeric(v); if (!/^(0|[1-9]\d*)$/.test(s)) throw new Error('Invalid report count.'); return s; }
function units(v: unknown) { const s = numeric(v); if (!/^\d+(\.\d+)?$/.test(s)) throw new Error('Invalid report units.'); return s; }
function percent(v: unknown) { if (v === null) return null; const s = units(v); if (Number(s) > 100) throw new Error('Invalid report percentage.'); return s; }
function goalMetric(v: unknown): GoalMetric { const g = object(v); return { goalId: text(g.goalId), title: text(g.title), titleMetadata: choice(g.titleMetadata, ['CURRENT']), unit: text(g.unit), completedUnits: units(g.completedUnits), plannedUnits: g.plannedUnits === null ? null : units(g.plannedUnits), percentage: percent(g.percentage), reason: nullableText(g.reason) }; }
export function dashboard(value: unknown): Dashboard {
  const v = object(value), p = object(v.period), t = object(v.tasks), h = object(v.habits), g = object(v.goals);
  if (p.cutoffExclusive !== true) throw new Error('Unsupported cutoff semantics.');
  return { period: { ...week(p), cutoff: text(p.cutoff), cutoffExclusive: true, calendarStatus: choice(p.calendarStatus, ['FROZEN','RECONSTRUCTED','UNFROZEN']), reportStatus: choice(p.reportStatus, ['PROVISIONAL','CLOSED_UNFINALIZED']), calculationVersion: count(p.calculationVersion) },
    tasks: { scheduled: count(t.scheduled), completed: count(t.completed), incomplete: count(t.incomplete), percentage: percent(t.percentage), reason: nullableText(t.reason) },
    habits: { successfulDays: count(h.successfulDays), eligibleDays: count(h.eligibleDays), percentage: percent(h.percentage), reason: nullableText(h.reason) },
    goals: { ...pageOf(goalMetric)(g), reason: nullableText(g.reason) } };
}
export interface Selection { weekStart: string; cutoff: string }
export function selectionError({ weekStart, cutoff }: Selection): string | undefined {
  if (!weekStart && !cutoff) return;
  if (!validDate(weekStart) || new Date(`${weekStart}T00:00:00Z`).getUTCDay() !== 1) return 'Choose a Monday between 1900 and 9998.';
  if (cutoff && (!/^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/.test(cutoff) || !validDate(cutoff.slice(0,10)))) return 'Enter an exact UTC instant, such as 2026-11-02T12:00:00.123456Z.';
}
export function reportPath(selection: Selection, page: number, size = 5) {
  const params = new URLSearchParams({ goalsPage: String(page), goalsSize: String(size) });
  if (selection.weekStart) params.set('weekStart', selection.weekStart);
  if (selection.cutoff) params.set('cutoff', selection.cutoff);
  return `/analytics/dashboard?${params}`;
}
