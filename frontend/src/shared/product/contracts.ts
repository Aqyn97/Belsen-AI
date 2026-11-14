import { LosslessNumber, version } from '../lib/numbers';
export interface Week {
    weekStart: string;
    timezone: string;
    startsAt: string;
    endsAt: string;
}
export interface Plan {
    id: string;
    reportingWeek: Week;
    plannedUnits: string;
    completedUnits: string;
    weeklyPercent: string;
    version: string;
}
export interface Goal {
    id: string;
    title: string;
    description: string | null;
    target: string;
    unit: string;
    startDate: string | null;
    deadline: string | null;
    status: 'ACTIVE' | 'ARCHIVED';
    archivedAt: string | null;
    version: string;
    lifetimeUnits: string;
    lifetimePercent: string;
    weeklyPlans: Plan[];
}
export interface Entry {
    id: string;
    submissionId: string;
    quantity: string;
    effectiveDate: string;
    createdAt: string;
    voidedAt: string | null;
    reportingWeek: Week;
}
export interface Task {
    id: string;
    title: string;
    description: string | null;
    goalId: string | null;
    originalDueDate: string | null;
    currentDueDate: string | null;
    originalReportingWeek: Week | null;
    estimatedDurationMinutes: number | null;
    status: 'OPEN' | 'COMPLETED';
    completedAt: string | null;
    archivedAt: string | null;
    createdAt: string;
    updatedAt: string;
    version: string;
    eventSequence: string;
}
export interface Page<T> {
    items: T[];
    page: number;
    size: number;
    totalElements: string;
}
export interface Period extends Week {
    cutoff: string;
    calendarStatus: string;
}
export function object(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Invalid server response.'); return value as Record<string, unknown>; }
export function text(value: unknown): string { if (typeof value !== 'string')
    throw new Error('Invalid server text.'); return value; }
function nullable(value: unknown): string | null { return value === null ? null : text(value); }
export function numeric(value: unknown): string { if (!(value instanceof LosslessNumber))
    throw new Error('Expected an exact JSON number.'); return value.toString(); }
function token(value: unknown): string { const result = numeric(value); version(result); return result; }
function integer(value: unknown, min: number, max: number): number { const raw = token(value); const n = Number(raw); if (!Number.isSafeInteger(n) || n < min || n > max)
    throw new Error('Invalid bounded integer.'); return n; }
function choices<T extends string>(value: unknown, options: T[]): T { if (!options.includes(value as T))
    throw new Error('Invalid server state.'); return value as T; }
export function week(value: unknown): Week { const v = object(value); return { weekStart: text(v.weekStart), timezone: text(v.timezone), startsAt: text(v.startsAt), endsAt: text(v.endsAt) }; }
export function plan(value: unknown): Plan { const v = object(value); return { id: text(v.id), reportingWeek: week(v.reportingWeek), plannedUnits: numeric(v.plannedUnits), completedUnits: numeric(v.completedUnits), weeklyPercent: numeric(v.weeklyPercent), version: token(v.version) }; }
export function goal(value: unknown): Goal {
    const v = object(value);
    if (!Array.isArray(v.weeklyPlans))
        throw new Error('Invalid weekly plans.');
    return { id: text(v.id), title: text(v.title), description: nullable(v.description), target: numeric(v.target), unit: text(v.unit), startDate: nullable(v.startDate), deadline: nullable(v.deadline), status: choices(v.status, ['ACTIVE', 'ARCHIVED']), archivedAt: nullable(v.archivedAt), version: token(v.version), lifetimeUnits: numeric(v.lifetimeUnits), lifetimePercent: numeric(v.lifetimePercent), weeklyPlans: v.weeklyPlans.map(plan) };
}
export function entry(value: unknown): Entry { const v = object(value); return { id: text(v.id), submissionId: text(v.submissionId), quantity: numeric(v.quantity), effectiveDate: text(v.effectiveDate), createdAt: text(v.createdAt), voidedAt: nullable(v.voidedAt), reportingWeek: week(v.reportingWeek) }; }
export function task(value: unknown): Task { const v = object(value); return { id: text(v.id), title: text(v.title), description: nullable(v.description), goalId: nullable(v.goalId), originalDueDate: nullable(v.originalDueDate), currentDueDate: nullable(v.currentDueDate), originalReportingWeek: v.originalReportingWeek === null ? null : week(v.originalReportingWeek), estimatedDurationMinutes: v.estimatedDurationMinutes === null ? null : integer(v.estimatedDurationMinutes, 1, 1440), status: choices(v.status, ['OPEN', 'COMPLETED']), completedAt: nullable(v.completedAt), archivedAt: nullable(v.archivedAt), createdAt: text(v.createdAt), updatedAt: text(v.updatedAt), version: token(v.version), eventSequence: token(v.eventSequence) }; }
export function pageOf<T>(decode: (value: unknown) => T) { return (value: unknown): Page<T> => { const v = object(value); if (!Array.isArray(v.items))
    throw new Error('Invalid server collection.'); return { items: v.items.map(decode), page: integer(v.page, 0, 2147483647), size: integer(v.size, 1, 100), totalElements: token(v.totalElements) }; }; }
export function period(value: unknown): Period { const v = object(object(value).period); return { ...week(v), cutoff: text(v.cutoff), calendarStatus: text(v.calendarStatus) }; }
