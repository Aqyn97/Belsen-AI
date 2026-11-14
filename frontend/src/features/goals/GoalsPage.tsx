import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { version } from '../../shared/lib/numbers';
import { goal, entry, pageOf, type Goal, type Entry, type Period } from '../../shared/product/contracts';
import { entityKey, productMessage, putEntity, serverFields, useOwner, useProductAction, useReporting, type Action } from '../../shared/product/data';
import { goalBody, goalDraft, uuid, validateGoal, type Errors } from '../../shared/product/forms';
import { ConfirmDialog, DraftConflict, Feedback, Heading, Pagination, Textarea, WeekInfo } from '../../shared/product/ui';
import { Button, EmptyState, Field, Status } from '../../shared/ui/components';
import { ProgressForm, WeeklyPlans } from './Planning';
export function GoalsPage() {
    const owner = useOwner();
    const client = useQueryClient();
    const action = useProductAction(owner);
    const [params, setParams] = useSearchParams();
    const selected = params.get('goal');
    const [page, setPage] = useState(0);
    const [creating, setCreating] = useState(false);
    const list = useQuery({ queryKey: ['goals', owner, 'list', page], queryFn: ({ signal }) => api.request(`/goals?page=${page}&size=20`, pageOf(goal), { signal }) });
    return <><Heading title="Goals"><Button disabled={action.pending} onClick={() => setCreating(!creating)}>{creating ? 'Close new goal' : 'New goal'}</Button></Heading><p>Give your direction a measure. Record real progress in the units that matter to you.</p><Feedback message={action.message} failed={action.failed}/>
    {creating && <section className="product-panel"><h2>Create a goal</h2><GoalEditor action={action} onSaved={value => { putEntity(client, 'goals', owner, value); setCreating(false); setParams({ goal: value.id }); }}/></section>}
    <div className="product-grid"><section aria-label="Goal list" className="product-list"><div className="list-toolbar"><h2>Your goals</h2><Button variant="secondary" disabled={action.pending || list.isFetching} onClick={() => { void list.refetch(); }}>Refresh goals</Button></div>
      {list.isFetching && <p role="status">{list.isPending ? 'Loading goals…' : 'Refreshing goals…'}</p>}{list.isError && <Status error>{productMessage(list.error)}</Status>}
      {list.data && <>{list.data.items.length === 0 ? <EmptyState title="Choose your first direction">Create a measurable goal, then add progress when you do the work.</EmptyState> : <ul className="resource-list">{list.data.items.map(value => <li key={value.id}><Button variant="secondary" disabled={action.pending} aria-pressed={value.id === selected} onClick={() => setParams({ goal: value.id })}><span>{value.title}<small>{value.status === 'ARCHIVED' ? 'Archived · ' : ''}{value.lifetimeUnits} / {value.target} {value.unit} · {value.lifetimePercent}% lifetime</small></span><span aria-hidden="true">↗</span></Button></li>)}</ul>}<Pagination label="Goals" data={list.data} page={page} onPage={setPage} disabled={action.pending}/></>}
    </section>{selected ? (uuid(selected) ? <GoalDetail key={selected} owner={owner} id={selected} action={action}/> : <Status error>Invalid goal link.</Status>) : <EmptyState title="One goal, a clear next step">Choose a goal to view details, weekly plans and progress history.</EmptyState>}</div></>;
}
function GoalEditor({ resource, action, onSaved, unitFixed = false }: {
    resource?: Goal;
    action: Action;
    onSaved: (value: Goal) => void;
    unitFixed?: boolean;
}) {
    const [draft, setDraft] = useState(() => goalDraft(resource));
    const [base, setBase] = useState(resource?.version);
    const [errors, setErrors] = useState<Errors>({});
    const stale = resource !== undefined && base !== resource.version;
    async function submit(event: FormEvent) {
        event.preventDefault();
        const effectiveDraft = unitFixed && resource ? { ...draft, unit: resource.unit } : draft;
        const errors = validateGoal(effectiveDraft);
        setErrors(errors);
        if (Object.keys(errors).length || stale)
            return;
        const result = await action.run(() => api.request(resource ? `/goals/${resource.id}` : '/goals', goal, { method: resource ? 'PATCH' : 'POST', body: goalBody(effectiveDraft, base) }), value => { setBase(value.version); onSaved(value); });
        if (!result.ok)
            setErrors(serverFields(result.error));
    }
    const field = (key: keyof typeof draft) => (value: string) => setDraft(old => ({ ...old, [key]: value }));
    return <form aria-label={resource ? 'Edit goal' : 'Create goal'} onSubmit={event => { void submit(event); }} noValidate><Feedback errors={errors}/><fieldset disabled={action.pending}>
    <DraftConflict stale={stale} onKeep={() => setBase(resource?.version)} onReplace={() => { setDraft(goalDraft(resource)); setBase(resource?.version); setErrors({}); }}/>
    <Field label="Goal title" required maxLength={200} value={draft.title} onChange={e => field('title')(e.target.value)} error={errors.title}/>
    <Textarea label="Goal description" maxLength={2000} value={draft.description} onChange={e => field('description')(e.target.value)} error={errors.description}/>
    <div className="form-columns"><Field label="Target quantity" required inputMode="decimal" value={draft.target} onChange={e => field('target')(e.target.value)} error={errors.target}/><Field label="Unit" required maxLength={50} value={unitFixed && resource ? resource.unit : draft.unit} disabled={unitFixed} onChange={e => field('unit')(e.target.value)} error={errors.unit}/></div>
    <p className="hint">Positive quantities allow up to 15 integer digits and four decimal places. Units become fixed once progress or a weekly plan exists.</p>
    <div className="form-columns"><Field label="Start date" type="date" min="1900-01-01" max="9998-12-31" value={draft.startDate} onChange={e => field('startDate')(e.target.value)} error={errors.startDate}/><Field label="Deadline" type="date" min="1900-01-01" max="9998-12-31" value={draft.deadline} onChange={e => field('deadline')(e.target.value)} error={errors.deadline}/></div>
    <Button type="submit" disabled={stale}>{action.pending ? 'Saving…' : resource ? 'Save goal' : 'Create goal'}</Button>
  </fieldset></form>;
}
function GoalDetail({ owner, id, action }: {
    owner: string;
    id: string;
    action: Action;
}) {
    const client = useQueryClient();
    const ref = useRef<HTMLHeadingElement>(null);
    const context = useReporting(owner);
    const [historyPage, setHistoryPage] = useState(0);
    const [confirm, setConfirm] = useState<{
        title: string;
        message: string;
        work: () => Promise<unknown>;
    } | null>(null);
    const query = useQuery({ queryKey: entityKey('goals', owner, id), queryFn: ({ signal }) => api.request(`/goals/${id}`, goal, { signal }) });
    const history = useQuery({ queryKey: ['goals', owner, 'history', id, historyPage], queryFn: ({ signal }) => api.request(`/goals/${id}/progress?page=${historyPage}&size=10`, pageOf(entry), { signal }) });
    const pendingSubmission = useQuery<{ submissionId: string } | null>({ queryKey: ['goals', owner, 'submission', id], queryFn: () => null, enabled: false, initialData: null, gcTime: Infinity }).data;
    const hasData = !!query.data;
    useEffect(() => { if (hasData)
        ref.current?.focus(); }, [hasData]);
    if (!query.data)
        return <section className="product-panel">{query.isPending ? <Status>Loading goal details…</Status> : <><Status error>{productMessage(query.error)}</Status><Button onClick={() => { void query.refetch(); }}>Retry goal details</Button></>}</section>;
    const value = query.data;
    const archived = value.status === 'ARCHIVED';
    const blocked = query.isError || action.pending;
    const period = context.data;
    function archive() { const expected = value.version; setConfirm({ title: 'Archive this goal?', message: 'Progress and weekly plans will remain in history. This goal will no longer accept changes.', work: () => api.request(`/goals/${id}/archive`, goal, { method: 'POST', body: { version: version(expected) } }).then(updated => { putEntity(client, 'goals', owner, updated); }) }); }
    function voidEntry(value: Entry) { setConfirm({ title: 'Void this progress entry?', message: 'The original record will remain visible. A replacement must be recorded separately; this is not an atomic correction.', work: () => api.request(`/goals/${id}/progress/${value.id}/void`, entry, { method: 'POST' }) }); }
    return <section className="product-panel detail-panel" aria-label="Goal details"><h2 ref={ref} tabIndex={-1}>{value.title}</h2><p className="state-label">{value.status === 'ARCHIVED' ? 'Archived' : 'Active goal'}</p><p className="description">{value.description || 'No description.'}</p><dl className="facts"><div><dt>Lifetime progress</dt><dd>{value.lifetimeUnits} / {value.target} {value.unit} · {value.lifetimePercent}%</dd></div><div><dt>Dates</dt><dd>{value.startDate || 'No start date'} → {value.deadline || 'No deadline'}</dd></div></dl><p className="hint">Lifetime and weekly progress use explicit entries, independently of task completion. Values above target remain visible.</p>
    {query.isFetching && <p role="status">Refreshing goal details…</p>}{query.isError && <Status error>Latest details could not be loaded. Your draft is retained; refresh before editing.</Status>}<Button variant="secondary" disabled={action.pending || query.isFetching} onClick={() => { void action.refresh(); }}>Refresh goal details</Button>
    {!archived && <fieldset disabled={blocked}><details open><summary>Edit goal</summary><GoalEditor resource={value} action={action} unitFixed={!!pendingSubmission || history.isPending || history.isError || value.weeklyPlans.length > 0 || BigInt(history.data?.totalElements ?? '0') > 0n} onSaved={updated => putEntity(client, 'goals', owner, updated)}/></details></fieldset>}
    <section className="detail-section"><h3>Reporting context</h3>{period ? <><WeekInfo value={period}/><p className="hint">Calendar checked at {period.cutoff}. </p><p className="hint">{period.calendarStatus === 'UNFROZEN' ? 'Preview only; the first write freezes this week.' : 'Reporting timezone follows the stored calendar.'}</p></> : <Status error={!context.isPending}>{context.isPending ? 'Loading reporting calendar…' : 'Reporting context is unavailable. Calendar-dependent writes are paused.'}</Status>}{context.isError && period && <Status error>Reporting context could not be refreshed. Calendar-dependent writes are paused until refresh succeeds.</Status>}<Button variant="secondary" disabled={action.pending || context.isFetching} onClick={() => { void context.refetch(); }}>Refresh reporting context</Button></section>
    {!archived && period && <fieldset disabled={blocked || context.isError}><ProgressForm owner={owner} goal={value} period={period} action={action}/></fieldset>}
    <section className="detail-section"><h3>Weekly plans</h3><p className="hint">A first target can be set during an open week. Once a planned week starts, that plan is fixed. Future targets can be edited using their own version.</p>{value.weeklyPlans.length === 0 && <p>No weekly targets yet. Recorded units still count toward lifetime progress.</p>}<ul className="plain-list">{value.weeklyPlans.map(plan => <li key={plan.id}><WeekInfo value={plan.reportingWeek}/><strong>{plan.completedUnits} / {plan.plannedUnits} {value.unit} · {plan.weeklyPercent}% weekly</strong></li>)}</ul>{!archived && period && <fieldset disabled={blocked || context.isError}><WeeklyPlans goal={value} period={period} action={action}/></fieldset>}</section>
    <section className="detail-section" aria-label="Progress history"><h3>Progress history</h3>{history.isFetching && <p role="status">Loading progress history…</p>}{history.isError && <><Status error>Progress history could not be loaded.</Status><Button onClick={() => { void history.refetch(); }}>Retry history</Button></>}{history.data && <>{history.data.items.length === 0 ? <p>No progress recorded yet.</p> : <ul className="plain-list">{history.data.items.map(item => <li key={item.id}><strong>{item.quantity} {value.unit} · {item.effectiveDate}</strong><WeekInfo value={item.reportingWeek}/><span>{item.voidedAt ? 'Voided · original retained' : `Recorded ${item.createdAt}`}</span>{!item.voidedAt && !archived && <Button variant="secondary" disabled={blocked || !openWeek(item.reportingWeek, period)} onClick={() => voidEntry(item)}>Void entry from {item.effectiveDate}</Button>}</li>)}</ul>}<Pagination label="Progress" data={history.data} page={historyPage} onPage={setHistoryPage} disabled={action.pending}/></>}</section>
    {!archived && <div className="detail-section"><Button variant="secondary" disabled={blocked} onClick={archive}>Archive goal</Button></div>}
    {confirm && <ConfirmDialog title={confirm.title} pending={action.pending} onCancel={() => setConfirm(null)} onConfirm={() => { void action.run(confirm.work).then(() => setConfirm(null)); }}>{confirm.message}</ConfirmDialog>}
  </section>;
}
function openWeek(week: {
    startsAt: string;
    endsAt: string;
}, period: Period | undefined) { return !!period && Date.parse(period.cutoff) >= Date.parse(week.startsAt) && Date.parse(period.cutoff) < Date.parse(week.endsAt); }
