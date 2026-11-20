import { object, text, numeric, week } from '../../shared/product/contracts';
import { type ReportPeriod } from '../analytics/model';
export const actions = { REVIEW_TASKS: 'Review task commitments', REVIEW_ROUTINE: 'Review routine feasibility', PLAN_TARGET: 'Make goal progress measurable', NEXT_GOAL_STEP: 'Choose a next goal step', REVIEW_NEXT_PLAN: 'Review the next plan' };
export const destinations = { GOALS: '/app/goals', TASKS: '/app/tasks', HABITS: '/app/habits' };
export interface Fact { id: string; actual: string; target: string | null; percentage: string | null; reason: string | null }
export interface Candidate { id: string; order: string; action: keyof typeof actions; destination: keyof typeof destinations; condition: string; title: string; nextAction: string; explanationTemplate: string; evidenceIds: string[] }
export interface Evidence { schemaVersion: string; period: ReportPeriod; facts: Fact[]; candidates: Candidate[]; coverage: { includedGoals: string; totalGoals: string; truncated: boolean } }
export interface Analysis { schemaVersion: string; submissionId: string; source: 'AI_PRIORITIZED' | 'RULES_ONLY'; preparedAt: string; expiresAt: string; evidence: Evidence; selectedIds: string[] }
export interface Submission { submissionId: string; weekStart: string; cutoff: string; noticeVersion: 1 }
function list<T>(v: unknown, decode: (x: unknown) => T, limit: number): T[] { if (!Array.isArray(v) || v.length > limit) throw new Error('Invalid Coach collection.'); return v.map(decode); }
function choice<T extends string>(v: unknown, values: readonly T[]): T { if (!values.includes(v as T)) throw new Error('Unsupported Coach state.'); return v as T; }
function count(v: unknown) { const s = numeric(v); if (!/^(0|[1-9]\d*)$/.test(s)) throw new Error('Invalid Coach count.'); return s; }
function units(v: unknown) { const s = text(v); if (!/^\d+(\.\d+)?$/.test(s)) throw new Error('Invalid Coach units.'); return s; }
function bool(v: unknown) { if (typeof v !== 'boolean') throw new Error('Invalid Coach flag.'); return v; }
const nullable = (v: unknown) => v === null ? null : text(v);
function instant(v: unknown) { const s = text(v); if (!/^\d{4}-\d\d-\d\dT.*Z$/.test(s) || !Number.isFinite(Date.parse(s))) throw new Error('Invalid Coach instant.'); return s; }
function unique(ids: string[]) { if (new Set(ids).size !== ids.length) throw new Error('Duplicate Coach identifiers.'); }
export function status(v: unknown) { const s = object(v); if (count(s.schemaVersion) !== '1' || count(s.noticeVersion) !== '1' || count(s.goalLimit) !== '20') throw new Error('Unsupported Coach contract.'); return choice(s.status, ['AVAILABLE', 'NOT_CONFIGURED'] as const); }
export function evidence(v: unknown): Evidence {
  const e = object(v), p = object(e.period), c = object(e.coverage);
  if (count(e.schemaVersion) !== '1' || p.cutoffExclusive !== true) throw new Error('Unsupported Coach evidence.');
  const facts = list(e.facts, x => { const f = object(x); const percentage = f.percentage === null ? null : units(f.percentage); if (percentage !== null && Number(percentage) > 100) throw new Error('Invalid percentage.'); return { id: text(f.id), actual: units(f.actual), target: f.target === null ? null : units(f.target), percentage, reason: nullable(f.reason) }; }, 22);
  const candidates = list(e.candidates, x => { const a = object(x); return { id: text(a.id), order: count(a.order), action: choice(a.action, Object.keys(actions) as (keyof typeof actions)[]), destination: choice(a.destination, Object.keys(destinations) as (keyof typeof destinations)[]), condition: text(a.condition), title: text(a.title), nextAction: text(a.nextAction), explanationTemplate: text(a.explanationTemplate), evidenceIds: list(a.evidenceIds, text, 22) }; }, 22);
  unique(facts.map(f => f.id)); unique(candidates.map(a => a.id));
  if (candidates.some(a => !a.evidenceIds.length || a.evidenceIds.some(id => !facts.some(f => f.id === id)))) throw new Error('Unverified evidence reference.');
  const includedGoals = count(c.includedGoals), totalGoals = count(c.totalGoals), truncated = bool(c.truncated);
  if (BigInt(includedGoals) > 20n || BigInt(totalGoals) < BigInt(includedGoals) || truncated !== (BigInt(totalGoals) > BigInt(includedGoals))) throw new Error('Invalid coverage.');
  return { schemaVersion: '1', period: { ...week(p), cutoff: instant(p.cutoff), cutoffExclusive: true, calendarStatus: choice(p.calendarStatus, ['FROZEN', 'RECONSTRUCTED', 'UNFROZEN'] as const), reportStatus: choice(p.reportStatus, ['PROVISIONAL', 'CLOSED_UNFINALIZED'] as const), calculationVersion: count(p.calculationVersion) }, facts, candidates, coverage: { includedGoals, totalGoals, truncated } };
}
export function analysis(v: unknown): Analysis {
  const a = object(v); if (count(a.schemaVersion) !== '1') throw new Error('Unsupported Coach response.');
  const snapshot = evidence(a.evidence), selectedIds = list(a.selectedIds, text, 3); unique(selectedIds);
  if (selectedIds.some(id => !snapshot.candidates.some(c => c.id === id))) throw new Error('Unknown recommendation.');
  const source = choice(a.source, ['AI_PRIORITIZED', 'RULES_ONLY'] as const);
  if (source === 'AI_PRIORITIZED' && !selectedIds.length || source === 'RULES_ONLY' && selectedIds.length) throw new Error('Invalid analysis source.');
  return { schemaVersion: '1', submissionId: text(a.submissionId), source, preparedAt: instant(a.preparedAt), expiresAt: instant(a.expiresAt), evidence: snapshot, selectedIds };
}
