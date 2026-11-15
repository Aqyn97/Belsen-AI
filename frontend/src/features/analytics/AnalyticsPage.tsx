import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { useSession } from '../auth/session';
import { Heading } from '../../shared/product/ui';
import { Button, Field, Status } from '../../shared/ui/components';
import { useDashboard } from './data';
import { selectionError, type Selection } from './model';
import { Report } from './Report';
const current: Selection = { weekStart: '', cutoff: '' };
export function reportError(error: unknown) {
  if (error instanceof ApiError) switch (error.problem.status) {
    case 400: return 'This reporting week or cutoff is unavailable. Choose a Monday and an exact cutoff after the server period start, at or before its end, and not in the future.';
    case 404: return 'Reporting history is unavailable for this week. No timezone or activity has been invented. Select another week or return to the current report.';
    case 409: return 'The reporting calendar cannot resolve this period, possibly during a timezone transition. Try another week or refresh later; your selection is preserved.';
    case 403: return 'This report is unavailable to the current session. Refresh your session before retrying.';
    case 401: return 'Your session ended. Sign in again.';
    case 429: return 'Too many requests. Wait before refreshing this report.';
  }
  return 'The report could not be loaded. Check your connection and try again.';
}
export function AnalyticsPage({ historical = false }: { historical?: boolean }) {
  const profile = useSession().data;
  const [draft, setDraft] = useState<Selection>(current);
  const [selection, setSelection] = useState<Selection>(current);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const focusGoals = useRef(false);
  const query = useDashboard(profile?.id ?? '', selection, page);
  useEffect(() => { document.getElementById('page-title')?.focus(); }, [historical]);
  useEffect(() => { if (focusGoals.current && query.data && !query.isFetching) { document.getElementById('weekly-goals-title')?.focus(); focusGoals.current = false; } }, [query.data, query.isFetching]);
  function apply(event: FormEvent) {
    event.preventDefault(); const problem = selectionError(draft); setError(problem ?? ''); if (problem) return;
    focusGoals.current = false; setPage(0); setSelection({ ...draft });
    if (page === 0 && draft.weekStart === selection.weekStart && draft.cutoff === selection.cutoff) void query.refetch();
  }
  function reset() { focusGoals.current = false; setDraft(current); setSelection(current); setPage(0); setError(''); if (!selection.weekStart && page === 0) void query.refetch(); }
  return <><Heading title={historical ? 'Analytics' : 'Dashboard'}><Button variant="secondary" disabled={query.isFetching} onClick={() => { void query.refetch(); }}>Refresh report</Button></Heading>
    <p className="analytics-intro">{historical ? 'Look back with the same measures, the original reporting calendar and each week’s own targets.' : `Welcome back, ${profile?.displayName ?? ''}. See what you accomplished, then choose your next small step.`}</p>
    {!historical && <nav className="dashboard-actions" aria-label="Next steps"><Link className="button button-primary" to="/app/goals">Create a goal</Link><Link className="button button-secondary" to="/app/tasks">Add a task</Link><Link className="button button-secondary" to="/app/habits">Open habits</Link><Link to="/app/analytics">View detailed analytics ↗</Link></nav>}
    {historical && <form className="analytics-selector" aria-label="Select reporting week" noValidate onSubmit={apply}><Field label="Reporting week (Monday)" type="date" min="1900-01-01" max="9998-12-31" value={draft.weekStart} onChange={e => setDraft({ ...draft, weekStart: e.target.value })}/><p className="hint">Leave blank for the current server reporting week. Earlier calendar history may be unavailable.</p><details><summary>Inspect an exact historical cutoff</summary><Field label="Exclusive cutoff (UTC, optional)" value={draft.cutoff} onChange={e => setDraft({ ...draft, cutoff: e.target.value })} placeholder="2026-11-02T12:00:00.123456Z"/><p className="hint">Requires a selected Monday. Only records accepted strictly before this instant count. Enter UTC with Z, up to nine fractional digits; the exact value is sent unchanged. The server validates its period boundaries and future limit.</p></details>{error && <Status error>{error}</Status>}<div className="actions"><Button type="submit">View report</Button><Button variant="secondary" onClick={reset}>Current reporting week</Button></div></form>}
    {query.isFetching && <Status>{query.data ? 'Refreshing report… Shown values are from the previous snapshot.' : 'Loading report…'}</Status>}
    {query.isError && <><Status error>{reportError(query.error)}{query.data && ' The displayed report is an older snapshot.'}</Status><Button variant="secondary" disabled={query.isFetching} onClick={() => { void query.refetch(); }}>Retry report</Button></>}
    {query.data && <Report value={query.data} page={page} onPage={next => { focusGoals.current = true; setPage(next); }} refreshing={query.isFetching}/>}
  </>;
}
