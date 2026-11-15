import { useId, useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { version } from '../../shared/lib/numbers';
import { Field, Button, Status } from '../../shared/ui/components';
import { DraftConflict, Feedback, Textarea } from '../../shared/product/ui';
import { putEntity, serverFields } from '../../shared/product/data';
import { habit, weekdays, validateSchedule, addDays, type Habit, type ScheduleDraft } from './model';
import { useHabitVersion, type HabitAction } from './data';
import type { Errors } from '../../shared/product/forms';
export function Weekdays({ value, onChange, error }: {
    value: number[];
    onChange: (value: number[]) => void;
    error?: string;
}) {
    const id = useId();
    return <fieldset className="weekday-selector" aria-describedby={error ? id : undefined}><legend>Scheduled weekdays</legend><div>{weekdays.map((name, index) => <label key={name}><input type="checkbox" checked={value.includes(index + 1)} onChange={e => onChange(e.target.checked ? [...new Set([...value, index + 1])].sort((a, b) => a - b) : value.filter(day => day !== index + 1))}/>{name}</label>)}</div>{error && <span id={id} className="field-error">{error}</span>}</fieldset>;
}
function ScheduleFields({ draft, setDraft, errors, revision = false }: {
    draft: ScheduleDraft;
    setDraft: (value: ScheduleDraft) => void;
    errors: Errors;
    revision?: boolean;
}) {
    return <>
 {revision && <Field label="Effective from" type="date" min="1900-01-01" max="9998-12-31" required value={draft.effectiveFrom} onChange={e => setDraft({ ...draft, effectiveFrom: e.target.value })} error={errors.effectiveFrom}/>}
 <Weekdays value={draft.weekdays} onChange={weekdays => setDraft({ ...draft, weekdays })} error={errors.weekdays}/><div className="form-columns"><Field label="Habit start date" type="date" min="1900-01-01" max="9998-12-31" required value={draft.startDate} onChange={e => setDraft({ ...draft, startDate: e.target.value })} error={errors.startDate}/><Field label="Habit end date (inclusive)" type="date" min="1900-01-01" max="9998-12-31" value={draft.endDate} onChange={e => setDraft({ ...draft, endDate: e.target.value })} error={errors.endDate}/></div>
 </>;
}
export function CreateHabit({ owner, today, action, onCreated }: {
    owner: string;
    today: string | null;
    action: HabitAction;
    onCreated: (habit: Habit) => void;
}) {
    const client = useQueryClient();
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [draft, setDraft] = useState<ScheduleDraft>({ weekdays: [1, 2, 3, 4, 5, 6, 7], startDate: today ?? '', endDate: '', effectiveFrom: '' });
    const [errors, setErrors] = useState<Errors>({});
    async function submit(e: FormEvent) {
        e.preventDefault();
        const errors = validateSchedule(draft, today, false);
        if (!title.trim() || title.trim().length > 200)
            errors.title = 'Use a title between 1 and 200 characters.';
        if (description.length > 2000)
            errors.description = 'Use at most 2,000 characters.';
        setErrors(errors);
        if (Object.keys(errors).length || action.isBusy())
            return;
        const result = await action.run(() => api.request('/habits', habit, { method: 'POST', body: { title: title.trim(), description: description || null, weekdays: draft.weekdays, startDate: draft.startDate, endDate: draft.endDate || null } }), value => { putEntity(client, 'habits', owner, value); onCreated(value); });
        if (!result.ok)
            setErrors(serverFields(result.error));
    }
    return <form aria-label="Create habit" noValidate onSubmit={e => { void submit(e); }}><Feedback errors={errors}/>{!today && <Status>Reporting context is unavailable. Enter an intentional start date; the server will validate it. No device date has been substituted.</Status>}<fieldset disabled={action.pending}><Field label="Habit title" required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} error={errors.title}/><Textarea label="Habit description" maxLength={2000} value={description} onChange={e => setDescription(e.target.value)} error={errors.description}/><ScheduleFields draft={draft} setDraft={setDraft} errors={errors}/><p className="hint">Creation starts today or later in your reporting calendar. The optional end date includes that day.</p><Button type="submit">Create habit</Button></fieldset></form>;
}
export function HabitEditor({ owner, value, action }: {
    owner: string;
    value: Habit;
    action: HabitAction;
}) {
    const client = useQueryClient();
    const latest = useHabitVersion(owner, value.id, value.version);
    const [base, setBase] = useState(value.version);
    const [title, setTitle] = useState(value.title);
    const [description, setDescription] = useState(value.description ?? '');
    const [errors, setErrors] = useState<Errors>({});
    const stale = base !== value.version || base !== latest;
    async function submit(e: FormEvent) {
        e.preventDefault();
        const errors: Errors = {};
        if (!title.trim() || title.trim().length > 200)
            errors.title = 'Use a title between 1 and 200 characters.';
        if (description.length > 2000)
            errors.description = 'Use at most 2,000 characters.';
        setErrors(errors);
        if (Object.keys(errors).length || stale)
            return;
        const result = await action.runHabit(value.id, base, () => api.request(`/habits/${value.id}`, habit, { method: 'PATCH', body: { version: version(base), title: title.trim(), description: description || null } }), response => { putEntity(client, 'habits', owner, response); setBase(response.version); });
        if (!result.ok)
            setErrors(serverFields(result.error));
    }
    return <form aria-label="Edit habit" noValidate onSubmit={e => { void submit(e); }}><Feedback errors={errors}/><fieldset disabled={action.pending}><fieldset disabled={value.version !== latest}><DraftConflict stale={stale} onKeep={() => setBase(value.version)} onReplace={() => { setBase(value.version); setTitle(value.title); setDescription(value.description ?? ''); }}/></fieldset>{value.version !== latest && <p>Refresh habit details to review the latest metadata.</p>}<Field label="Habit title" required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} error={errors.title}/><Textarea label="Habit description" maxLength={2000} value={description} onChange={e => setDescription(e.target.value)} error={errors.description}/><Button type="submit" disabled={stale}>Save habit</Button></fieldset></form>;
}
export function ScheduleEditor({ owner, value, today, action }: {
    owner: string;
    value: Habit;
    today: string | null;
    action: HabitAction;
}) {
    const client = useQueryClient();
    const latest = useHabitVersion(owner, value.id, value.version);
    const source = value.latestPendingSchedule ?? value.currentSchedule;
    const initial = (): ScheduleDraft => ({ weekdays: source?.weekdays ?? [], startDate: source?.startDate ?? (today ? addDays(today, 1) : ''), endDate: source?.endDate ?? '', effectiveFrom: value.latestPendingSchedule?.effectiveFrom ?? (today ? addDays(today, 1) : '') });
    const [draft, setDraft] = useState(initial);
    const [base, setBase] = useState(value.version);
    const [errors, setErrors] = useState<Errors>({});
    const stale = base !== value.version || base !== latest;
    async function submit(e: FormEvent) {
        e.preventDefault();
        const errors = validateSchedule(draft, today, true);
        setErrors(errors);
        if (Object.keys(errors).length || stale)
            return;
        const result = await action.runHabit(value.id, base, () => api.request(`/habits/${value.id}/schedules`, habit, { method: 'POST', body: { version: version(base), effectiveFrom: draft.effectiveFrom, weekdays: draft.weekdays, startDate: draft.startDate, endDate: draft.endDate || null } }), response => { putEntity(client, 'habits', owner, response); setBase(response.version); });
        if (!result.ok)
            setErrors(serverFields(result.error));
    }
    return <form aria-label="Revise schedule" noValidate onSubmit={e => { void submit(e); }}><Feedback errors={errors}/><fieldset disabled={action.pending}><fieldset disabled={value.version !== latest}><DraftConflict stale={stale} onKeep={() => setBase(value.version)} onReplace={() => { setBase(value.version); setDraft(initial()); }}/></fieldset>{value.version !== latest && <p>Refresh habit details before adopting a new version.</p>}<ScheduleFields draft={draft} setDraft={setDraft} errors={errors} revision/><p className="hint">Revisions apply tomorrow or later. Start/end bound the schedule; effective-from controls when this revision can apply. Newer accepted revisions can supersede pending changes where their dates overlap. Past obligations and completions remain unchanged.</p><Button type="submit" disabled={stale}>Save future schedule</Button></fieldset></form>;
}
