import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import { decimalInput, version } from '../../shared/lib/numbers';
import { entry, plan, type Goal, type Period } from '../../shared/product/contracts';
import { monday, quantityError, reportingDate, validDate, type Errors } from '../../shared/product/forms';
import { serverFields, type Action } from '../../shared/product/data';
import { DraftConflict, Feedback } from '../../shared/product/ui';
import { Button, Field, Status } from '../../shared/ui/components';
interface Submission {
    submissionId: string;
    quantity: string;
    effectiveDate: string;
    conflict?: boolean;
    collision?: boolean;
}
export function ProgressForm({ owner, goal, period, action }: {
    owner: string;
    goal: Goal;
    period: Period;
    action: Action;
}) {
    const client = useQueryClient();
    const key = ['goals', owner, 'submission', goal.id];
    const intent = useQuery<Submission | null>({ queryKey: key, queryFn: () => null, enabled: false, initialData: null, gcTime: Infinity }).data;
    const [quantity, setQuantity] = useState('');
    const [date, setDate] = useState(() => reportingDate(period.cutoff, period.timezone));
    const [errors, setErrors] = useState<Errors>({});
    async function submit(event: FormEvent) {
        event.preventDefault();
        if (action.isBusy())
            return;
        const errors: Errors = {};
        const invalid = quantityError(quantity);
        if (!intent && invalid)
            errors.quantity = invalid;
        if (!intent && (!validDate(date) || date < period.weekStart || date > reportingDate(period.cutoff, period.timezone)))
            errors.effectiveDate = 'Use an elapsed date in the server reporting week.';
        if (!intent && goal.startDate && date < goal.startDate)
            errors.effectiveDate = 'Progress cannot precede the goal start date.';
        setErrors(errors);
        if (Object.keys(errors).length)
            return;
        const submission = intent ?? { submissionId: crypto.randomUUID(), quantity, effectiveDate: date };
        client.setQueryData(key, submission);
        const outcome = await action.run(() => api.request(`/goals/${goal.id}/progress`, entry, { method: 'POST', body: { submissionId: submission.submissionId, quantity: decimalInput(submission.quantity), effectiveDate: submission.effectiveDate } }), () => { client.setQueryData(key, null); setQuantity(''); });
        if (client.getQueryData<{
            id: string;
        } | null>(['session'])?.id !== owner)
            return;
        if (!outcome.ok) {
            setErrors(serverFields(outcome.error));
            if (outcome.error instanceof ApiError) {
                if (outcome.error.problem.status === 409)
                    client.setQueryData(key, { ...submission, conflict: true, collision: outcome.error.problem.detail === 'Submission identifier was already used with different progress.' });
                else if ([400, 403, 404].includes(outcome.error.problem.status))
                    client.setQueryData(key, null);
            }
        }
    }
    return <section className="detail-section"><h3>Record progress</h3><Feedback errors={errors}/>{intent && <Status error>{intent.collision ? 'This submission ID already belongs to different progress. No replacement was sent. Review history before starting a separate entry.' : intent.conflict ? 'This submission was rejected with a conflict. Its key and payload remain unchanged. Check history and the open-week rules before starting a separate entry.' : 'An entry is awaiting confirmation. Retry sends exactly the same submission ID, date and quantity, including after a lost response. Do not record a replacement until the result is known.'}</Status>}<form aria-label="Record progress" onSubmit={event => { void submit(event); }} noValidate><fieldset disabled={action.pending || !!intent}>
    <Field label={`Progress quantity (${goal.unit})`} value={intent?.quantity ?? quantity} inputMode="decimal" required onChange={e => setQuantity(e.target.value)} error={errors.quantity}/><Field label="Progress date" type="date" min={period.weekStart} max={reportingDate(period.cutoff, period.timezone)} value={intent?.effectiveDate ?? date} required onChange={e => setDate(e.target.value)} error={errors.effectiveDate}/></fieldset><Button type="submit" disabled={action.pending || intent?.conflict}>{action.pending ? 'Recording…' : intent ? 'Retry same progress submission' : 'Record progress'}</Button></form>
    {intent?.conflict && <Button variant="secondary" disabled={action.pending} onClick={() => client.setQueryData(key, null)}>Start separate entry after reviewing history</Button>}<p className="hint">Retries are retained in memory while this account session remains open. Reloading or signing out discards this local retry intent; check history before submitting again. Voiding and recording a replacement are separate actions.</p>
  </section>;
}
export function WeeklyPlans({ goal, period, action }: {
    goal: Goal;
    period: Period;
    action: Action;
}) {
    const initial = goal.weeklyPlans.find(value => value.reportingWeek.weekStart === period.weekStart);
    const [weekStart, setWeekStart] = useState(period.weekStart);
    const [quantity, setQuantity] = useState(initial?.plannedUnits ?? '');
    const [base, setBase] = useState(initial?.version);
    const [errors, setErrors] = useState<Errors>({});
    const current = goal.weeklyPlans.find(value => value.reportingWeek.weekStart === weekStart);
    const stale = base !== current?.version;
    const fixed = !!current && Date.parse(period.cutoff) >= Date.parse(current.reportingWeek.startsAt);
    async function submit(event: FormEvent) {
        event.preventDefault();
        const errors: Errors = {};
        const invalid = quantityError(quantity);
        if (invalid)
            errors.plannedUnits = invalid;
        if (!monday(weekStart))
            errors.weekStart = 'Choose a valid Monday (1900–9998).';
        if (weekStart < period.weekStart)
            errors.weekStart = 'Choose the current reporting week or a future week.';
        setErrors(errors);
        if (Object.keys(errors).length || stale || fixed)
            return;
        const result = await action.run(() => api.request(`/goals/${goal.id}/weekly-plans/${weekStart}`, plan, { method: 'PUT', body: { plannedUnits: decimalInput(quantity), ...(base === undefined ? {} : { version: version(base) }) } }), saved => setBase(saved.version));
        if (!result.ok)
            setErrors(serverFields(result.error));
    }
    return <form aria-label="Weekly plan" onSubmit={event => { void submit(event); }} noValidate><Feedback errors={errors}/><DraftConflict stale={stale} onKeep={() => setBase(current?.version)} onReplace={() => { setBase(current?.version); setQuantity(current?.plannedUnits ?? ''); }}/>
    <Field label="Plan week (Monday)" type="date" min={period.weekStart} max="9998-12-24" value={weekStart} required onChange={e => { const value = e.target.value; setWeekStart(value); const selected = goal.weeklyPlans.find(p => p.reportingWeek.weekStart === value); setBase(selected?.version); setQuantity(selected?.plannedUnits ?? ''); }} error={errors.weekStart}/><Field label={`Planned units (${goal.unit})`} inputMode="decimal" value={quantity} required onChange={e => setQuantity(e.target.value)} error={errors.plannedUnits}/>
    {fixed && <Status>This weekly plan has started and cannot be changed.</Status>}<p className="hint">The server freezes the selected week's timezone when saved. Future weeks may use a different reporting timezone.</p><Button type="submit" disabled={action.pending || stale || fixed}>{current ? 'Save weekly plan' : 'Create weekly plan'}</Button>
  </form>;
}
