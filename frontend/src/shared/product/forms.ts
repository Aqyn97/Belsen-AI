import { decimalInput, version } from '../lib/numbers';
import type { Goal, Task } from './contracts';
export type Errors = Record<string, string>;
export const uuid = (value: string | null) => !!value && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
// UTC is used only for civil-date arithmetic. No date-only value passes through device-local midnight.
export function validDate(value: string): boolean {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01' || value > '9998-12-31')
        return false;
    const date = new Date(`${value}T00:00:00Z`);
    return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
export function monday(value: string) { return validDate(value) && new Date(`${value}T00:00:00Z`).getUTCDay() === 1; }
export function nextWeek(value: string) { const d = new Date(`${value}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + 7); return d.toISOString().slice(0, 10); }
export function reportingDate(cutoff: string, timezone: string) { const parts = new Intl.DateTimeFormat('en', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(cutoff)); const part = (name: string) => parts.find(p => p.type === name)?.value; return `${part('year')}-${part('month')}-${part('day')}`; }
export function quantityError(value: string): string | undefined { try {
    decimalInput(value);
    return undefined;
}
catch {
    return 'Use a positive quantity: at most 15 integer digits and 4 decimal places.';
} }
function stringErrors(title: string, description: string): Errors { const errors: Errors = {}; if (!title.trim() || title.trim().length > 200)
    errors.title = 'Use a title between 1 and 200 characters.'; if (description.length > 2000)
    errors.description = 'Use at most 2,000 characters.'; return errors; }
export interface GoalDraft {
    title: string;
    description: string;
    target: string;
    unit: string;
    startDate: string;
    deadline: string;
}
export const goalDraft = (goal?: Goal): GoalDraft => ({ title: goal?.title ?? '', description: goal?.description ?? '', target: goal?.target ?? '', unit: goal?.unit ?? '', startDate: goal?.startDate ?? '', deadline: goal?.deadline ?? '' });
export function validateGoal(draft: GoalDraft): Errors {
    const e = stringErrors(draft.title, draft.description);
    const q = quantityError(draft.target);
    if (q)
        e.target = q;
    if (!draft.unit.trim() || draft.unit.trim().length > 50)
        e.unit = 'Use a unit between 1 and 50 characters.';
    for (const k of ['startDate', 'deadline'] as const)
        if (draft[k] && !validDate(draft[k]))
            e[k] = 'Enter a valid date (1900–9998).';
    if (draft.startDate && draft.deadline && draft.deadline < draft.startDate)
        e.deadline = 'Deadline must be on or after the start date.';
    return e;
}
export const goalBody = (draft: GoalDraft, token?: string) => ({ ...draft, title: draft.title.trim(), unit: draft.unit.trim(), description: draft.description || null, startDate: draft.startDate || null, deadline: draft.deadline || null, target: decimalInput(draft.target), ...(token === undefined ? {} : { version: version(token) }) });
export interface TaskDraft {
    title: string;
    description: string;
    goalId: string;
    dueDate: string;
    estimatedDurationMinutes: string;
}
export const taskDraft = (task?: Task): TaskDraft => ({ title: task?.title ?? '', description: task?.description ?? '', goalId: task?.goalId ?? '', dueDate: task?.currentDueDate ?? '', estimatedDurationMinutes: task?.estimatedDurationMinutes?.toString() ?? '' });
export function validateTask(draft: TaskDraft, create: boolean): Errors {
    const e = stringErrors(draft.title, draft.description);
    if (draft.goalId && !uuid(draft.goalId))
        e.goalId = 'Select an owned goal.';
    if (create && draft.dueDate && !validDate(draft.dueDate))
        e.dueDate = 'Enter a valid date (1900–9998).';
    if (draft.estimatedDurationMinutes && (!/^\d{1,4}$/.test(draft.estimatedDurationMinutes) || Number(draft.estimatedDurationMinutes) < 1 || Number(draft.estimatedDurationMinutes) > 1440))
        e.estimatedDurationMinutes = 'Use whole minutes between 1 and 1,440.';
    return e;
}
export function taskBody(draft: TaskDraft, current?: Task, token?: string) {
    const body = { title: draft.title.trim(), description: draft.description || null, estimatedDurationMinutes: draft.estimatedDurationMinutes ? Number(draft.estimatedDurationMinutes) : null };
    return current ? { ...body, version: version(token ?? current.version), ...(draft.goalId !== (current.goalId ?? '') ? { goalId: draft.goalId || null } : {}) } : { ...body, goalId: draft.goalId || null, dueDate: draft.dueDate || null };
}
export interface Filters {
    status: string;
    archived: boolean;
    goalId: string;
    dateMode: string;
    dueDate: string;
    from: string;
    to: string;
}
export const emptyFilters: Filters = { status: '', archived: false, goalId: '', dateMode: 'exact', dueDate: '', from: '', to: '' };
export function filterError(filters: Filters): string | undefined {
    for (const value of filters.dateMode === 'exact' ? [filters.dueDate] : [filters.from, filters.to])
        if (value && !validDate(value))
            return 'Enter valid filter dates.';
    if (filters.dateMode === 'range' && filters.from && filters.to && filters.from > filters.to)
        return 'The end date must be on or after the start date.';
    return undefined;
}
export function taskParams(filters: Filters, page: number) {
    const params = new URLSearchParams({ page: String(page), size: '20', archived: String(filters.archived) });
    if (filters.status)
        params.set('status', filters.status);
    if (filters.goalId)
        params.set('goalId', filters.goalId);
    for (const key of filters.dateMode === 'exact' ? ['dueDate'] as const : ['from', 'to'] as const)
        if (filters[key])
            params.set(key, filters[key]);
    return params.toString();
}
