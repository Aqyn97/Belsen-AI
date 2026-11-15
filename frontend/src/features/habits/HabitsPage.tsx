import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { version } from '../../shared/lib/numbers';
import { pageOf, type Period } from '../../shared/product/contracts';
import { useOwner, useReporting, putEntity, productMessage } from '../../shared/product/data';
import { uuid } from '../../shared/product/forms';
import { ConfirmDialog, Feedback, Heading, Pagination, Select, WeekInfo } from '../../shared/product/ui';
import { Button, EmptyState, Status } from '../../shared/ui/components';
import { habit, dayLabel, reportingLocalDate, type Habit } from './model';
import { habitKey, useHabitAction, useHabitVersion, useHistory, firstPages, latestHistoryVersion, type HabitAction } from './data';
import { CreateHabit, HabitEditor, ScheduleEditor } from './Editors';
import { RestPlanner, ScheduleInfo, HistoryPanel } from './HistoryPanel';
import { DayControls } from './DayControls';
export function HabitsPage() {
    const owner = useOwner();
    const action = useHabitAction(owner);
    const reporting = useReporting(owner);
    const [params, setParams] = useSearchParams();
    const selected = params.get('habit');
    const [page, setPage] = useState(0);
    const [archived, setArchived] = useState(false);
    const [creating, setCreating] = useState(false);
    const list = useQuery({ queryKey: ['habits', owner, 'list', page, archived], queryFn: ({ signal }) => api.request(`/habits?page=${page}&size=5&archived=${archived}`, pageOf(habit), { signal }) });
    const today = reporting.data ? reportingLocalDate(reporting.data.cutoff, reporting.data.timezone) : null;
    useEffect(() => { document.getElementById('page-title')?.focus(); }, []);
    return <><Heading title="Habits"><Button disabled={action.pending} onClick={() => setCreating(!creating)}>{creating ? 'Close new habit' : 'New habit'}</Button></Heading><p>Build a rhythm that fits your life. Rest and unscheduled days are not incomplete habits.</p><Feedback message={action.notice || action.message} failed={!!action.notice || action.failed}/>
 <section className="reporting-strip" aria-label="Habit reporting calendar">{reporting.data ? <><strong>Reporting date: {today}</strong><WeekInfo value={reporting.data}/><small>Calendar checked at {reporting.data.cutoff}{reporting.data.calendarStatus === 'UNFROZEN' ? ' · preview; first write freezes the week' : ''}</small></> : <Status error={reporting.isError}>{reporting.isPending ? 'Loading reporting calendar…' : 'The reporting date is unavailable. No device-local date has been substituted.'}</Status>}{reporting.data && !today && <Status error>The browser cannot interpret the returned reporting date or timezone. No device-local date has been substituted.</Status>}{reporting.isError && reporting.data && <Status error>Calendar refresh failed; shown context is older. Check current day evidence before acting.</Status>}<Button variant="secondary" disabled={action.pending || reporting.isFetching} onClick={() => { void action.refresh(); }}>Refresh habits and reporting context</Button></section>
 {creating && <section className="product-panel"><h2>Create a routine</h2>{reporting.isPending ? <Status>Loading reporting calendar for habit creation…</Status> : <CreateHabit owner={owner} today={reporting.isError ? null : today} action={action} onCreated={value => { setCreating(false); setArchived(false); setParams({ habit: value.id }); }}/>}</section>}
 <div className="product-grid"><section aria-label="Habit overview"><div className="list-toolbar"><h2>Daily checklist for this page</h2><Select label="Habit collection" value={String(archived)} disabled={action.pending} onChange={e => { setArchived(e.target.value === 'true'); setPage(0); }}><option value="false">Active habits</option><option value="true">Archived habits</option></Select></div><p className="hint">Up to five routines per page. Browse other pages for more obligations. Archived habits can still have earlier obligations.</p>
 {list.isFetching && <Status>{list.isPending ? 'Loading habits…' : 'Refreshing habits…'}</Status>}{list.isError && <><Status error>{productMessage(list.error)}</Status><Button onClick={() => { void list.refetch(); }}>Retry habits</Button></>}{list.data && <>{list.data.items.length === 0 ? <EmptyState title={archived ? 'No archived routines' : 'Start a small daily rhythm'}>{archived ? 'Archived habits retain their history.' : 'Create a routine and choose the weekdays that suit you.'}</EmptyState> : <ul className="habit-board">{list.data.items.map(value => <DailyRow key={value.id} owner={owner} value={value} period={reporting.isError ? undefined : reporting.data} action={action} selected={selected === value.id} onSelect={() => setParams({ habit: value.id })}/>)}</ul>}<Pagination label="Habits" data={list.data} page={page} onPage={setPage} disabled={action.pending}/></>}</section>
 {selected ? (uuid(selected) ? <HabitDetail key={selected} owner={owner} id={selected} today={reporting.isError ? null : today} period={reporting.isError ? undefined : reporting.data} calendarPending={reporting.isPending} action={action}/> : <Status error>Invalid habit link.</Status>) : <EmptyState title="A routine, understood">Choose a habit for schedules, rest planning and reconstructed history.</EmptyState>}</div></>;
}
function DailyRow({ owner, value, period, action, selected, onSelect }: {
    owner: string;
    value: Habit;
    period: Period | undefined;
    action: HabitAction;
    selected: boolean;
    onSelect: () => void;
}) {
    const today = period ? reportingLocalDate(period.cutoff, period.timezone) ?? '' : '';
    const query = useHistory(owner, value.id, { from: today, to: today, cutoff: '' }, { ...firstPages, size: 1 }, !!today);
    const observed = latestHistoryVersion(query.data);
    useHabitVersion(owner, value.id, observed ?? value.version);
    const day = query.data?.days.items[0];
    const currentDate = day?.reportingDay ? reportingLocalDate(query.data!.cutoff, day.reportingDay.week.timezone) : null;
    return <li className="habit-card" aria-label={`Daily habit ${value.title}`}><Button variant="secondary" className="habit-title" aria-pressed={selected} disabled={action.pending} onClick={onSelect}>{value.title}</Button>{value.status === 'ARCHIVED' && <p>Archived · scheduling stops {value.archiveEffectiveDate}</p>}{value.currentSchedule && <p className="hint">Current schedule starts {value.currentSchedule.startDate}</p>}{!today ? <p>Reporting date unavailable. Open history or refresh the calendar.</p> : query.isPending ? <Status>Checking today's obligation…</Status> : query.isError ? <><Status error>Today's state could not be loaded.</Status><Button variant="secondary" onClick={() => { void query.refetch(); }}>Retry daily state</Button></> : day && query.data ? <><strong className="habit-state">{day.localDate} · {dayLabel(day)}</strong>{day.restReason && <p>{day.restReason}</p>}{currentDate !== today ? <p>Reporting day changed or its context is unavailable. Refresh before checking off this routine.</p> : <DayControls owner={owner} id={value.id} value={day} snapshot={query.data} action={action}/>}</> : <p>No day evidence returned. No failure has been inferred.</p>}</li>;
}
function HabitDetail({ owner, id, today, period, calendarPending, action }: {
    owner: string;
    id: string;
    today: string | null;
    period: Period | undefined;
    calendarPending: boolean;
    action: HabitAction;
}) {
    const client = useQueryClient();
    const heading = useRef<HTMLHeadingElement>(null);
    const [archiveVersion, setArchiveVersion] = useState<string | null>(null);
    const query = useQuery({ queryKey: habitKey(owner, id), queryFn: ({ signal }) => api.request(`/habits/${id}`, habit, { signal }) });
    const latest = useHabitVersion(owner, id, query.data?.version);
    const ready = !!query.data;
    useEffect(() => { if (ready)
        heading.current?.focus(); }, [ready]);
    if (!query.data)
        return <section className="product-panel">{query.isPending ? <Status>Loading habit details…</Status> : <><Status error>{productMessage(query.error)}</Status><Button onClick={() => { void query.refetch(); }}>Retry habit details</Button></>}</section>;
    const value = query.data;
    const from = period?.weekStart ?? value.reportingWeek?.weekStart ?? '';
    const to = today ?? from;
    const archived = value.status === 'ARCHIVED';
    async function archive() { if (archiveVersion === null)
        return; await action.runHabit(id, archiveVersion, () => api.request(`/habits/${id}/archive`, habit, { method: 'POST', body: { version: version(archiveVersion) } }), saved => putEntity(client, 'habits', owner, saved)); setArchiveVersion(null); }
    return <section className="product-panel detail-panel" aria-label="Habit details"><h2 tabIndex={-1} ref={heading}>{value.title}</h2><p>{archived ? `Archived · future scheduling disabled from ${value.archiveEffectiveDate}` : 'Active routine'}</p><p className="description">{value.description || 'No description.'}</p>{query.isFetching && <Status>Refreshing habit details…</Status>}{query.isError && <Status error>Habit refresh failed. Drafts remain; refresh before editing.</Status>}<Button variant="secondary" disabled={action.pending || query.isFetching} onClick={() => { void action.refresh(); }}>Refresh habit details</Button>
 <ScheduleInfo value={value.currentSchedule} label="Current schedule"/><ScheduleInfo value={value.latestPendingSchedule} label="Latest pending schedule"/><p className="hint">Only the most recently accepted pending revision is shown here. Earlier revisions can still apply before a newer revision's effective date; per-date history resolves the actual schedule.</p>
 {!archived && <fieldset disabled={action.pending || query.isError}><details open><summary>Edit habit metadata</summary><HabitEditor owner={owner} value={value} action={action}/></details><details className="detail-section"><summary>Revise future schedule</summary>{calendarPending ? <Status>Loading calendar for schedule planning…</Status> : <ScheduleEditor owner={owner} value={value} today={today} action={action}/>}</details></fieldset>}
 {calendarPending ? <Status>Loading calendar for rest and history…</Status> : <><RestPlanner owner={owner} value={value} today={today} action={action}/><HistoryPanel owner={owner} value={value} initialFrom={from} initialTo={to} action={action}/></>}
 {!archived && <div className="detail-section"><Button variant="secondary" disabled={action.pending || query.isError || value.version !== latest} onClick={() => setArchiveVersion(value.version)}>Archive habit</Button></div>}
 {archiveVersion !== null && <ConfirmDialog title="Archive this habit?" pending={action.pending} onCancel={() => setArchiveVersion(null)} onConfirm={() => { void archive(); }}>Future scheduling stops from tomorrow in the reporting calendar. Earlier obligations, today's check-ins, accepted rest decisions and history remain. There is no unarchive action.</ConfirmDialog>}
 </section>;
}
