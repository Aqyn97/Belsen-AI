import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { version } from '../../shared/lib/numbers';
import { task, goal, pageOf, type Task } from '../../shared/product/contracts';
import { entityKey, productMessage, putEntity, serverFields, useOwner, useProductAction, type Action } from '../../shared/product/data';
import { emptyFilters, filterError, taskBody, taskDraft, taskParams, uuid, validDate, validateTask, type Errors } from '../../shared/product/forms';
import { ConfirmDialog, DraftConflict, Feedback, GoalPicker, Heading, Pagination, Select, Textarea, WeekInfo } from '../../shared/product/ui';
import { Button, EmptyState, Field, Status } from '../../shared/ui/components';
export function TasksPage() {
    const owner = useOwner();
    const client = useQueryClient();
    const action = useProductAction(owner);
    const [params, setParams] = useSearchParams();
    const selected = params.get('task');
    const [page, setPage] = useState(0);
    const [creating, setCreating] = useState(false);
    const [draft, setDraft] = useState(emptyFilters);
    const [filters, setFilters] = useState(emptyFilters);
    const [filterMessage, setFilterMessage] = useState('');
    const queryString = taskParams(filters, page);
    const list = useQuery({ queryKey: ['tasks', owner, 'list', queryString], queryFn: ({ signal }) => api.request(`/tasks?${queryString}`, pageOf(task), { signal }) });
    function apply(event: FormEvent) { event.preventDefault(); const error = filterError(draft); setFilterMessage(error ?? ''); if (error)
        return; setPage(0); setFilters({ ...draft }); }
    return <><Heading title="Tasks"><Button disabled={action.pending} onClick={() => setCreating(!creating)}>{creating ? 'Close new task' : 'New task'}</Button></Heading><p>A clear next action. Organize your day without losing your original commitments.</p><Feedback message={action.message} failed={action.failed}/>
    {creating && <section className="product-panel"><h2>Create a task</h2><TaskEditor owner={owner} action={action} onSaved={value => { putEntity(client, 'tasks', owner, value); setCreating(false); setParams({ task: value.id }); }}/></section>}
    <details className="filter-panel"><summary>Filter tasks</summary><form aria-label="Task filters" noValidate onSubmit={apply}><Feedback message={filterMessage}/><fieldset disabled={action.pending}><div className="form-columns"><Select label="Task status" value={draft.status} onChange={e => setDraft({ ...draft, status: e.target.value })}><option value="">Open and completed</option><option value="OPEN">Open</option><option value="COMPLETED">Completed</option></Select><Select label="Task collection" value={String(draft.archived)} onChange={e => setDraft({ ...draft, archived: e.target.value === 'true' })}><option value="false">Active only</option><option value="true">Archived only</option></Select></div><GoalPicker owner={owner} value={draft.goalId} onChange={goalId => setDraft({ ...draft, goalId })} label="Filter by goal" emptyLabel="All tasks regardless of goal"/>
      <Select label="Due-date filter" value={draft.dateMode} onChange={e => setDraft({ ...draft, dateMode: e.target.value })}><option value="exact">Exact current due date</option><option value="range">Inclusive current due-date range</option></Select>{draft.dateMode === 'exact' ? <Field label="Filter due date" type="date" value={draft.dueDate} onChange={e => setDraft({ ...draft, dueDate: e.target.value })}/> : <div className="form-columns"><Field label="Due from" type="date" value={draft.from} onChange={e => setDraft({ ...draft, from: e.target.value })}/><Field label="Due to" type="date" value={draft.to} onChange={e => setDraft({ ...draft, to: e.target.value })}/></div>}
      <div className="actions"><Button type="submit">Apply filters</Button><Button variant="secondary" onClick={() => { setDraft(emptyFilters); setFilters(emptyFilters); setPage(0); setFilterMessage(''); }}>Reset filters</Button></div></fieldset></form></details>
    <div className="product-grid"><section aria-label="Task list" className="product-list"><div className="list-toolbar"><h2>{filters.archived ? 'Archived tasks' : 'Your tasks'}</h2><Button variant="secondary" disabled={action.pending || list.isFetching} onClick={() => { void list.refetch(); }}>Refresh tasks</Button></div>{list.isFetching && <p role="status">{list.isPending ? 'Loading tasks…' : 'Refreshing tasks…'}</p>}{list.isError && <Status error>{productMessage(list.error)}</Status>}{list.data && <>{list.data.items.length === 0 ? <EmptyState title="Room for your next action">Create a task or adjust the active filters.</EmptyState> : <ul className="resource-list">{list.data.items.map(value => <li key={value.id}><Button variant="secondary" aria-pressed={value.id === selected} disabled={action.pending} onClick={() => setParams({ task: value.id })}><span>{value.title}<small>{value.archivedAt ? 'Archived · ' : ''}{value.status === 'OPEN' ? 'Open' : 'Completed'} · {value.currentDueDate || 'No due date'}</small></span><span aria-hidden="true">↗</span></Button></li>)}</ul>}<Pagination label="Tasks" data={list.data} page={page} onPage={setPage} disabled={action.pending}/></>}</section>
    {selected ? (uuid(selected) ? <TaskDetail key={selected} owner={owner} id={selected} action={action}/> : <Status error>Invalid task link.</Status>) : <EmptyState title="A manageable next step">Choose a task to review, complete or reschedule it.</EmptyState>}</div></>;
}
function TaskEditor({ owner, resource, action, onSaved }: {
    owner: string;
    resource?: Task;
    action: Action;
    onSaved: (value: Task) => void;
}) {
    const [draft, setDraft] = useState(() => taskDraft(resource));
    const [base, setBase] = useState(resource?.version);
    const [errors, setErrors] = useState<Errors>({});
    const stale = !!resource && resource.version !== base;
    async function submit(event: FormEvent) {
        event.preventDefault();
        const errors = validateTask(draft, !resource);
        setErrors(errors);
        if (Object.keys(errors).length || stale)
            return;
        const outcome = await action.run(() => api.request(resource ? `/tasks/${resource.id}` : '/tasks', task, { method: resource ? 'PATCH' : 'POST', body: taskBody(draft, resource, base) }), saved => { setBase(saved.version); onSaved(saved); });
        if (!outcome.ok)
            setErrors(serverFields(outcome.error));
    }
    return <form aria-label={resource ? 'Edit task' : 'Create task'} onSubmit={event => { void submit(event); }} noValidate><Feedback errors={errors}/><fieldset disabled={action.pending}><DraftConflict stale={stale} onKeep={() => setBase(resource?.version)} onReplace={() => { setBase(resource?.version); setDraft(taskDraft(resource)); setErrors({}); }}/>
    <Field label="Task title" maxLength={200} required value={draft.title} onChange={e => setDraft({ ...draft, title: e.target.value })} error={errors.title}/><Textarea label="Task description" maxLength={2000} value={draft.description} onChange={e => setDraft({ ...draft, description: e.target.value })} error={errors.description}/><GoalPicker owner={owner} value={draft.goalId} onChange={goalId => setDraft({ ...draft, goalId })} activeOnly/>
    {!resource && <Field label="Due date" type="date" min="1900-01-01" max="9998-12-31" value={draft.dueDate} onChange={e => setDraft({ ...draft, dueDate: e.target.value })} error={errors.dueDate}/>}<Field label="Estimated duration (minutes)" inputMode="numeric" value={draft.estimatedDurationMinutes} onChange={e => setDraft({ ...draft, estimatedDurationMinutes: e.target.value })} error={errors.estimatedDurationMinutes}/><p className="hint">Optional whole minutes, from 1 to 1,440. Dates and completion state use the separate actions below.</p><Button type="submit" disabled={stale}>{action.pending ? 'Saving…' : resource ? 'Save task' : 'Create task'}</Button>
  </fieldset></form>;
}
function TaskDetail({ owner, id, action }: {
    owner: string;
    id: string;
    action: Action;
}) {
    const client = useQueryClient();
    const ref = useRef<HTMLHeadingElement>(null);
    const [confirm, setConfirm] = useState(false);
    const query = useQuery({ queryKey: entityKey('tasks', owner, id), queryFn: ({ signal }) => api.request(`/tasks/${id}`, task, { signal }) });
    const associated = useQuery({ queryKey: entityKey('goals', owner, query.data?.goalId ?? ''), enabled: !!query.data?.goalId, queryFn: ({ signal }) => api.request(`/goals/${query.data!.goalId}`, goal, { signal }) });
    const hasData = !!query.data;
    useEffect(() => { if (hasData)
        ref.current?.focus(); }, [hasData]);
    if (!query.data)
        return <section className="product-panel">{query.isPending ? <Status>Loading task details…</Status> : <><Status error>{productMessage(query.error)}</Status><Button onClick={() => { void query.refetch(); }}>Retry task details</Button></>}</section>;
    const value = query.data;
    const blocked = action.pending || query.isError;
    const archived = !!value.archivedAt;
    function perform(verb: 'complete' | 'reopen' | 'archive') { const expected = value.version; return action.run(() => api.request(`/tasks/${id}/${verb}`, task, { method: 'POST', body: { version: version(expected) } }), saved => putEntity(client, 'tasks', owner, saved)); }
    return <section className="product-panel detail-panel" aria-label="Task details"><h2 ref={ref} tabIndex={-1}>{value.title}</h2><p className="state-label">{archived ? 'Archived · ' : ''}{value.status === 'OPEN' ? 'Open' : 'Completed'}</p><p className="description">{value.description || 'No description.'}</p><dl className="facts"><div><dt>Current due date</dt><dd>{value.currentDueDate || 'No current due date'}</dd></div><div><dt>Original reporting due date</dt><dd>{value.originalDueDate || 'Not assigned yet'}</dd></div><div><dt>Estimated duration</dt><dd>{value.estimatedDurationMinutes === null ? 'Not set' : `${value.estimatedDurationMinutes} minutes`}</dd></div><div><dt>Associated goal</dt><dd>{value.goalId ? <Link to={`/app/goals?goal=${value.goalId}`}>{associated.data?.title ?? 'View associated goal'}</Link> : 'No goal'}</dd></div></dl>{value.originalReportingWeek && <WeekInfo value={value.originalReportingWeek}/>}<p className="hint">Rescheduling changes your plan, but never moves the original obligation out of reports. Archiving retains task history. Completing a task does not record goal units.</p>
    {query.isFetching && <p role="status">Refreshing task details…</p>}{query.isError && <Status error>Latest state could not be loaded. Your drafts remain; refresh before editing.</Status>}<Button variant="secondary" disabled={action.pending || query.isFetching} onClick={() => { void action.refresh(); }}>Refresh task details</Button>
    {!archived && <fieldset disabled={blocked}><div className="actions detail-section"><Button onClick={() => { void perform(value.status === 'OPEN' ? 'complete' : 'reopen'); }}>{value.status === 'OPEN' ? 'Complete task' : 'Reopen task'}</Button></div><details open><summary>Edit task metadata</summary><TaskEditor owner={owner} resource={value} action={action} onSaved={saved => putEntity(client, 'tasks', owner, saved)}/></details><Reschedule value={value} owner={owner} action={action}/><div className="detail-section"><Button variant="secondary" onClick={() => setConfirm(true)}>Archive task</Button></div></fieldset>}
    {confirm && <ConfirmDialog title="Archive this task?" pending={action.pending} onCancel={() => setConfirm(false)} onConfirm={() => { void perform('archive').then(() => setConfirm(false)); }}>The task leaves the active list. Its original due date, completion state and immutable reporting history remain.</ConfirmDialog>}
  </section>;
}
function Reschedule({ value, owner, action }: {
    value: Task;
    owner: string;
    action: Action;
}) {
    const client = useQueryClient();
    const [date, setDate] = useState(value.currentDueDate ?? '');
    const [base, setBase] = useState(value.version);
    const [errors, setErrors] = useState<Errors>({});
    const stale = base !== value.version;
    async function submit(event: FormEvent) {
        event.preventDefault();
        const errors: Errors = {};
        if (date && !validDate(date))
            errors.dueDate = 'Enter a valid date (1900–9998), or leave blank to remove it.';
        setErrors(errors);
        if (Object.keys(errors).length || stale || value.status !== 'OPEN')
            return;
        const outcome = await action.run(() => api.request(`/tasks/${value.id}/reschedule`, task, { method: 'POST', body: { version: version(base), dueDate: date || null } }), saved => { putEntity(client, 'tasks', owner, saved); setBase(saved.version); setDate(saved.currentDueDate ?? ''); });
        if (!outcome.ok)
            setErrors(serverFields(outcome.error));
    }
    return <section className="detail-section"><h3>Reschedule</h3>{value.status === 'COMPLETED' && <p>Reopen this task before rescheduling.</p>}<form aria-label="Reschedule task" noValidate onSubmit={event => { void submit(event); }}><Feedback errors={errors}/><fieldset disabled={action.pending || value.status !== 'OPEN'}><DraftConflict stale={stale} onKeep={() => setBase(value.version)} onReplace={() => { setBase(value.version); setDate(value.currentDueDate ?? ''); }}/><Field label="New due date" type="date" min="1900-01-01" max="9998-12-31" value={date} onChange={e => setDate(e.target.value)} error={errors.dueDate}/><p className="hint">Leave blank to remove the current due date. The original reporting due date is retained.</p><Button type="submit" disabled={stale}>Save due date</Button></fieldset></form></section>;
}
