import { useState, type FormEvent } from 'react';
import { Field, Button, Status } from '../../shared/ui/components';
import { Feedback, Pagination, WeekInfo } from '../../shared/product/ui';
import { productMessage } from '../../shared/product/data';
import { validDate } from '../../shared/product/forms';
import { useHabitVersion, latestHistoryVersion, useHistory, firstPages, type HabitAction } from './data';
import { DayControls } from './DayControls';
import { addDays, dayLabel, rangeError, weekdayText, type Habit, type HistoryRange, type Schedule } from './model';
export function ScheduleInfo({ value, label }: {
    value: Schedule | null;
    label: string;
}) { return <section className="schedule-info"><h3>{label}</h3>{value ? <><p>{value.enabled ? weekdayText(value.weekdays) : 'Scheduling disabled'}</p><p>Effective {value.effectiveFrom} · schedule starts {value.startDate}{value.endDate ? ` · ends ${value.endDate} inclusive` : ''}</p><small>Accepted revision {value.sequence} at {value.recordedAt}</small></> : <p>No applicable schedule is available in this view.</p>}</section>; }
export function RestPlanner({ owner, value, today, action }: {
    owner: string;
    value: Habit;
    today: string | null;
    action: HabitAction;
}) {
    const [date, setDate] = useState(today ? addDays(today, 1) : '');
    const [checked, setChecked] = useState('');
    const [error, setError] = useState('');
    const query = useHistory(owner, value.id, { from: checked, to: checked, cutoff: '' }, { ...firstPages, size: 1 }, validDate(checked));
    function check(e: FormEvent) { e.preventDefault(); if (!validDate(date)) {
        setError('Choose a valid reporting date (1900–9998).');
        return;
    } setError(''); setChecked(date); if (date === checked)
        void query.refetch(); }
    const day = query.data?.days.items[0];
    return <section className="detail-section" aria-label="Plan rest"><h3>Plan rest before the day starts</h3><p>Check the selected date's reporting context first. Rest removes only this habit's obligation and is never a completion.</p><form aria-label="Check rest date" noValidate onSubmit={check}><Feedback message={error}/><Field label="Rest date" type="date" value={date} onChange={e => setDate(e.target.value)} min="1900-01-01" max="9998-12-31"/><Button type="submit" variant="secondary" disabled={action.pending || query.isFetching}>Check reporting day</Button></form>
 {checked === date && query.isFetching && <Status>Checking reporting day…</Status>}{checked === date && query.isError && <Status error>{productMessage(query.error)}</Status>}{checked === date && day && query.data && <div className="habit-day"><strong>{day.localDate} · {dayLabel(day)}</strong>{day.reportingDay ? <><WeekInfo value={day.reportingDay.week}/><p>Rest deadline: {day.reportingDay.startsAt} · checked at {query.data.cutoff}</p></> : <p>No authoritative reporting-day context. Rest actions are unavailable.</p>}<DayControls key={day.localDate} owner={owner} id={value.id} value={day} snapshot={query.data} action={action} allowRest allowDaily={false} disabled={query.isError}/> {day.started && <p>This day has already started. Rest cannot be planned or cancelled.</p>}</div>}
 </section>;
}
export function HistoryPanel({ owner, value, initialFrom, initialTo, action }: {
    owner: string;
    value: Habit;
    initialFrom: string;
    initialTo: string;
    action: HabitAction;
}) {
    const [draft, setDraft] = useState<HistoryRange>({ from: initialFrom, to: initialTo, cutoff: '' });
    const [range, setRange] = useState(draft);
    const [pages, setPages] = useState(firstPages);
    const [error, setError] = useState('');
    const query = useHistory(owner, value.id, range, pages, !rangeError(range));
    const latest = useHabitVersion(owner, value.id, latestHistoryVersion(query.data));
    function apply(e: FormEvent) { e.preventDefault(); const error = rangeError(draft); setError(error ?? ''); if (error)
        return; setPages(firstPages); setRange({ ...draft }); if (JSON.stringify(draft) === JSON.stringify(range))
        void query.refetch(); }
    const maxSequence = [value.currentSchedule?.sequence, value.latestPendingSchedule?.sequence].filter((v): v is string => !!v).reduce((max, v) => BigInt(v) > BigInt(max) ? v : max, '0');
    return <section className="detail-section" aria-label="Habit history"><h3>History, reconstructed by the server</h3><form aria-label="Habit history range" noValidate onSubmit={apply}><Feedback message={error}/><fieldset disabled={action.pending}><div className="form-columns"><Field label="History from" type="date" min="1900-01-01" max="9998-12-31" value={draft.from} onChange={e => setDraft({ ...draft, from: e.target.value })}/><Field label="History to" type="date" min="1900-01-01" max="9998-12-31" value={draft.to} onChange={e => setDraft({ ...draft, to: e.target.value })}/></div><Field label="Historical cutoff (UTC, optional)" placeholder="2026-11-02T12:00:00Z" value={draft.cutoff} onChange={e => setDraft({ ...draft, cutoff: e.target.value })}/><p className="hint">At most 93 dates. Leave cutoff blank for current state. An explicit UTC cutoff excludes actions accepted exactly at that instant; historical views are read-only.</p><Button type="submit">Load habit history</Button></fieldset></form>
 {query.isFetching && <Status>Loading habit history…</Status>}{query.isError && <Status error>{productMessage(query.error)}</Status>}{query.data && <><p className="history-caption">Displayed {range.from} through {range.to} · {query.data.exclusiveCutoff ? 'Historical state, exclusive cutoff' : 'Current committed state'} · {query.data.cutoff}</p><p className="hint">Title and version tokens are current metadata. Day states and applied schedules come from server reconstruction. Each response is one snapshot; changing pages can observe later commits.</p><h4>Reporting days</h4><ul className="plain-list">{query.data.days.items.map(day => <li key={day.localDate}><strong>{day.localDate} · {dayLabel(day)}</strong>{day.schedule && <span>Applied revision {day.schedule.sequence} · {day.schedule.enabled ? weekdayText(day.schedule.weekdays) : 'Scheduling disabled'}</span>}{day.reportingDay ? <><WeekInfo value={day.reportingDay.week}/><span>{day.reportingDay.startsAt} — {day.reportingDay.endsAt}</span></> : <span>Reporting-day context is unavailable or omitted for an unstarted historical date.</span>}{day.restReason && <p>Rest reason: {day.restReason}</p>}<DayControls owner={owner} id={value.id} value={day} snapshot={query.data!} action={action} disabled={query.isError}/></li>)}</ul><Pagination label="History days" data={query.data.days} page={pages.page} onPage={page => setPages({ ...pages, page })} disabled={action.pending}/>
 <h4>Accepted actions</h4>{query.data.actions.items.length === 0 && <p>No actions on this page. Missing actions are not assumed failures.</p>}<ul className="plain-list">{query.data.actions.items.map(event => <li key={event.id}><strong>{event.localDate} · {event.action.replaceAll('_', ' ').toLowerCase()}</strong><span>Accepted {event.acceptedAt} · event sequence {event.sequence}</span>{event.reason && <p>{event.reason}</p>}</li>)}</ul><Pagination label="History actions" data={query.data.actions} page={pages.actionPage} onPage={actionPage => setPages({ ...pages, actionPage })} disabled={action.pending}/>
 <h4>Accepted schedules</h4><ul className="plain-list">{query.data.schedules.items.map(schedule => <li key={schedule.id}><ScheduleInfo value={schedule} label={`Revision ${schedule.sequence}`}/>{query.data!.exclusiveCutoff ? <span>Schedule record accepted before this cutoff. Per-date rows identify where it applied.</span> : schedule.id === value.currentSchedule?.id ? <span>Current schedule in the latest detail view.</span> : schedule.id === value.latestPendingSchedule?.id ? <span>Latest accepted pending change.</span> : BigInt(schedule.sequence) < BigInt(maxSequence) ? <span>Earlier revision; superseded only where a later accepted revision applies. Earlier obligations remain.</span> : <span>Accepted revision. Per-date history above identifies which revision applies.</span>}</li>)}</ul><Pagination label="History schedules" data={query.data.schedules} page={pages.schedulePage} onPage={schedulePage => setPages({ ...pages, schedulePage })} disabled={action.pending}/><p className="hint">Habit-wide version {latest} is a current concurrency token, not historical metadata. No lifetime streak is inferred.</p></>}
 </section>;
}
