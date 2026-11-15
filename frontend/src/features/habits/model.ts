import { object, text, numeric, pageOf, week, type Page, type Week } from '../../shared/product/contracts';
import { version } from '../../shared/lib/numbers';
import { validDate, reportingDate, type Errors } from '../../shared/product/forms';
export interface Schedule {
    id: string;
    sequence: string;
    effectiveFrom: string;
    startDate: string;
    endDate: string | null;
    weekdays: number[];
    enabled: boolean;
    recordedAt: string;
}
export interface Habit {
    id: string;
    title: string;
    description: string | null;
    status: 'ACTIVE' | 'ARCHIVED';
    archivedAt: string | null;
    archiveEffectiveDate: string | null;
    version: string;
    createdAt: string;
    updatedAt: string;
    reportingWeek: Week | null;
    currentSchedule: Schedule | null;
    latestPendingSchedule: Schedule | null;
}
export interface ReportingDay {
    localDate: string;
    startsAt: string;
    endsAt: string;
    week: Week;
}
export interface Day {
    localDate: string;
    started: boolean;
    scheduled: boolean;
    completed: boolean;
    restPlanned: boolean;
    restApplicable: boolean;
    eligible: boolean;
    restReason: string | null;
    version: string;
    schedule: Schedule | null;
    reportingDay: ReportingDay | null;
}
export interface Event {
    id: string;
    localDate: string;
    sequence: string;
    action: string;
    acceptedAt: string;
    completed: boolean;
    restPlanned: boolean;
    reason: string | null;
    reportingWeekId: string;
}
export interface History {
    habitId: string;
    cutoff: string;
    exclusiveCutoff: boolean;
    days: Page<Day>;
    actions: Page<Event>;
    schedules: Page<Schedule>;
}
const nullable = (value: unknown) => value === null ? null : text(value);
const bool = (value: unknown): boolean => { if (typeof value !== 'boolean')
    throw new Error('Invalid habit state.'); return value; };
const token = (value: unknown) => { const s = numeric(value); version(s); return s; };
export function schedule(value: unknown): Schedule { const v = object(value); if (!Array.isArray(v.weekdays))
    throw new Error('Invalid weekdays.'); const weekdays = v.weekdays.map(d => { const n = Number(numeric(d)); if (!Number.isInteger(n) || n < 1 || n > 7)
    throw new Error('Invalid ISO weekday.'); return n; }); if (new Set(weekdays).size !== weekdays.length)
    throw new Error('Duplicate weekdays.'); return { id: text(v.id), sequence: token(v.sequence), effectiveFrom: text(v.effectiveFrom), startDate: text(v.startDate), endDate: nullable(v.endDate), weekdays, enabled: bool(v.enabled), recordedAt: text(v.recordedAt) }; }
export function habit(value: unknown): Habit { const v = object(value); if (v.status !== 'ACTIVE' && v.status !== 'ARCHIVED')
    throw new Error('Invalid habit status.'); return { id: text(v.id), title: text(v.title), description: nullable(v.description), status: v.status, archivedAt: nullable(v.archivedAt), archiveEffectiveDate: nullable(v.archiveEffectiveDate), version: token(v.version), createdAt: text(v.createdAt), updatedAt: text(v.updatedAt), reportingWeek: v.reportingWeek === null ? null : week(v.reportingWeek), currentSchedule: v.currentSchedule === null ? null : schedule(v.currentSchedule), latestPendingSchedule: v.latestPendingSchedule === null ? null : schedule(v.latestPendingSchedule) }; }
export function day(value: unknown): Day { const v = object(value); const context = v.reportingDay === null ? null : object(v.reportingDay); return { localDate: text(v.localDate), started: bool(v.started), scheduled: bool(v.scheduled), completed: bool(v.completed), restPlanned: bool(v.restPlanned), restApplicable: bool(v.restApplicable), eligible: bool(v.eligible), restReason: nullable(v.restReason), version: token(v.version), schedule: v.schedule === null ? null : schedule(v.schedule), reportingDay: context ? { localDate: text(context.localDate), startsAt: text(context.startsAt), endsAt: text(context.endsAt), week: week(context.week) } : null }; }
function event(value: unknown): Event { const v = object(value); return { id: text(v.id), localDate: text(v.localDate), sequence: token(v.sequence), action: text(v.action), acceptedAt: text(v.acceptedAt), completed: bool(v.completed), restPlanned: bool(v.restPlanned), reason: nullable(v.reason), reportingWeekId: text(v.reportingWeekId) }; }
export function history(value: unknown): History { const v = object(value); return { habitId: text(v.habitId), cutoff: text(v.cutoff), exclusiveCutoff: bool(v.exclusiveCutoff), days: pageOf(day)(v.days), actions: pageOf(event)(v.actions), schedules: pageOf(schedule)(v.schedules) }; }
export const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const weekdayText = (values: number[]) => values.map(v => weekdays[v - 1]).join(', ');
export function addDays(value: string, count: number) { const d = new Date(`${value}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + count); return d.toISOString().slice(0, 10); }
export function dayLabel(value: Day): string {
    if (value.restApplicable)
        return 'Planned rest — not completion';
    if (value.restPlanned)
        return 'Rest decision retained — not applicable';
    if (value.completed)
        return 'Completed';
    if (!value.scheduled)
        return 'Not scheduled';
    if (!value.started)
        return value.reportingDay ? 'Scheduled — not started' : 'Not started or reporting context unavailable';
    return value.eligible ? 'Scheduled — not complete' : 'Ineligible';
}
export function dayPermissions(value: Day, snapshot: History, latest: string) {
    const context = value.reportingDay;
    const at = Date.parse(snapshot.cutoff);
    const current = !snapshot.exclusiveCutoff && value.version === latest && !!context;
    const open = current && at >= Date.parse(context!.week.startsAt) && at < Date.parse(context!.week.endsAt);
    const before = current && at < Date.parse(context!.startsAt);
    return { complete: !!open && value.started && value.scheduled && value.eligible && !value.completed && !value.restPlanned, undo: !!open && value.started && value.scheduled && value.completed && !value.restPlanned, rest: !!before && value.scheduled && !value.completed && !value.restPlanned, cancelRest: !!before && value.restPlanned };
}
export interface ScheduleDraft {
    weekdays: number[];
    startDate: string;
    endDate: string;
    effectiveFrom: string;
}
export function validateSchedule(draft: ScheduleDraft, today: string | null, revision: boolean): Errors {
    const errors: Errors = {};
    if (draft.weekdays.length === 0 || draft.weekdays.length > 7 || new Set(draft.weekdays).size !== draft.weekdays.length || draft.weekdays.some(d => !Number.isInteger(d) || d < 1 || d > 7))
        errors.weekdays = 'Choose one to seven distinct weekdays.';
    if (!validDate(draft.startDate))
        errors.startDate = 'Enter a valid start date (1900–9998).';
    if (draft.endDate && (!validDate(draft.endDate) || draft.endDate < draft.startDate))
        errors.endDate = 'End date is inclusive and must be on or after the start date.';
    if (!revision && today && draft.startDate < today)
        errors.startDate = 'A new habit starts today or later in the reporting calendar.';
    if (revision && (!validDate(draft.effectiveFrom) || (today && draft.effectiveFrom <= today)))
        errors.effectiveFrom = 'A revision must become effective tomorrow or later.';
    return errors;
}
export interface HistoryRange {
    from: string;
    to: string;
    cutoff: string;
}
export function rangeError(range: HistoryRange): string | undefined {
    if (!validDate(range.from) || !validDate(range.to) || range.from > range.to)
        return 'Choose an ordered range of valid reporting dates.';
    if ((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86400000 >= 93)
        return 'History can contain at most 93 dates, including both endpoints.';
    if (range.cutoff && (!validDate(range.cutoff.slice(0, 10)) || !(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/).test(range.cutoff) || !Number.isFinite(Date.parse(range.cutoff))))
        return 'Use a UTC cutoff such as 2026-11-02T12:00:00Z. The server rejects future cutoffs.';
    return undefined;
}

// Unsupported browser timezone data must never trigger a device-local fallback.
export function reportingLocalDate(cutoff:string,timezone:string):string|null {
 try {const value=reportingDate(cutoff,timezone);return validDate(value)?value:null;}catch{return null;}
}
