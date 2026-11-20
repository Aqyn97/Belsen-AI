import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import { useSession } from '../auth/session';
import { Heading, WeekInfo } from '../../shared/product/ui';
import { Button, Field, Status } from '../../shared/ui/components';
import { dashboard, reportPath, selectionError, type Selection } from '../analytics/model';
import { reportError } from '../analytics/AnalyticsPage';
import { analysis, status, destinations, type Analysis, type Submission } from './model';
interface Attempt { body: Submission; result?: Analysis; canReplace?: boolean }
const current: Selection = { weekStart: '', cutoff: '' };
export function coachError(error: unknown) {
  if (error instanceof ApiError) switch (error.problem.status) {
    case 400: return 'This reporting selection could not be analyzed. Review the week and cutoff.';
    case 401: return 'Your session ended. Sign in again.';
    case 403: return 'Your security token needs refreshing or the action is forbidden. Nothing was retried. Check your session before recovering this same request.';
    case 404: return 'Reporting history is unavailable for this period.';
    case 409: return 'This request is pending, conflicts with recorded data, or has an uncertain provider outcome. No new analysis was started. Recover only this same request; uncertain operations may require operator review.';
    case 413: return 'The request is too large. No analysis was started. Prepare a new selection.';
    case 415: return 'The request format is unsupported. No analysis was started. Prepare a new selection.';
    case 410: return 'The 24-hour replay window has expired. This submission will not be retried.';
    case 429: return `The daily quota or free-tier capacity is unavailable. ${error.retryAfter && /^\d+$/.test(error.retryAfter) ? `Wait at least ${error.retryAfter} seconds. ` : ''}No automatic retry was made; uncertain operations may still require operator review.`;
    case 502: return 'The provider returned an unusable ranking. No successful AI result was displayed. Recovering the same request will not dispatch it again.';
    case 503: return 'AI Coach is disabled, unconfigured or temporarily unavailable. Your verified progress remains available. No automatic retry was made.';
    case 504: return 'The provider timed out; its outcome may be uncertain. No automatic retry was made. Recover this same request rather than starting another.';
  }
  return 'The response could not be confirmed. The provider may have received the request. Recover the same submission; do not assume the analysis failed before dispatch.';
}
export function CoachPage() { const profile = useSession().data; return profile ? <Workspace key={profile.id} owner={profile.id}/> : null; }
function Workspace({ owner }: { owner: string }) {
  const client = useQueryClient(); const key = ['coach', owner, 'attempt'];
  const [attempt, setAttempt] = useState<Attempt | undefined>(() => { const saved = client.getQueryData<Attempt>(key); return saved?.result && Date.parse(saved.result.expiresAt) <= Date.now() ? { body: saved.body } : saved; });
  const [draft, setDraft] = useState<Selection>(current), [selection, setSelection] = useState<Selection>(current);
  const [canReplace, setCanReplace] = useState(attempt?.canReplace === true);
  const [consent, setConsent] = useState(false), [pending, setPending] = useState(false), [error, setError] = useState('');
  const running = useRef(false), lifecycle = useRef({ epoch: 0, controller: undefined as AbortController | undefined });
  const resultHeading = useRef<HTMLHeadingElement>(null);
  const availability = useQuery({ queryKey: ['coach', owner, 'status'], queryFn: ({ signal }) => api.request('/coach/status', status, { signal }), retry: false });
  const report = useQuery({ queryKey: ['coach', owner, 'preview', selection.weekStart, selection.cutoff], queryFn: ({ signal }) => api.request(reportPath(selection, 0, 20), dashboard, { signal }), retry: false });
  useEffect(() => { const state = lifecycle.current; document.getElementById('page-title')?.focus(); return () => { state.epoch++; state.controller?.abort(); }; }, []);
  function remember(value: Attempt | undefined) { client.setQueryDefaults(key, { gcTime: 24 * 60 * 60 * 1000 }); if (value) client.setQueryData(key, value); else client.removeQueries({ queryKey: key, exact: true }); setAttempt(value); }
  function apply(event: FormEvent) {
    event.preventDefault(); const problem = selectionError(draft); if (problem) { setError(problem); return; }
    lifecycle.current.epoch++; lifecycle.current.controller?.abort(); running.current = false; setPending(false); setError('');setSelection({ ...draft });
    // Retain the previous submission key for explicit recovery even when the preview changes.
  }
  async function submit(recover: boolean) {
    if (running.current || !consent || availability.data !== 'AVAILABLE') return;
    const body = recover ? attempt?.body : report.data ? { submissionId: crypto.randomUUID(), weekStart: report.data.period.weekStart, cutoff: report.data.period.cutoff, noticeVersion: 1 as const } : undefined;
    if (!body) return;
    running.current = true; setPending(true); setError('');setCanReplace(false); const epoch = ++lifecycle.current.epoch;
    const abort = new AbortController(); lifecycle.current.controller = abort; remember({ body });
    try {
      const result = await api.request('/coach/analyses', analysis, { method: 'POST', body, signal: abort.signal });
      if (epoch !== lifecycle.current.epoch) return;
      if (result.submissionId !== body.submissionId || result.evidence.period.weekStart !== body.weekStart || result.evidence.period.cutoff !== body.cutoff) throw new Error('Response does not match the submitted period.');
      remember({ body, result }); requestAnimationFrame(() => resultHeading.current?.focus());
    } catch (failure) { if (epoch === lifecycle.current.epoch) { const replaceable = failure instanceof ApiError && [400, 404, 410, 413, 415].includes(failure.problem.status);
      setError(coachError(failure));setCanReplace(replaceable);remember({ body, canReplace: replaceable }); } }
    finally { if (epoch === lifecycle.current.epoch) { running.current = false;setPending(false); } }
  }
  const result = attempt?.result;
  return <div className="coach-page"><Heading title="AI Coach"/><p className="analytics-intro">Understand your progress and choose what to focus on next.</p>
    <form className="analytics-selector" aria-label="Coach reporting period" onSubmit={apply} noValidate><Field label="Reporting week (Monday)" type="date" min="1900-01-01" max="9998-12-31" value={draft.weekStart} onChange={e => setDraft({ ...draft, weekStart: e.target.value })}/><p className="hint">Leave blank for the current server reporting week. Calendar history may be unavailable.</p><details><summary>Choose an exact historical cutoff</summary><Field label="Exclusive cutoff (UTC, optional)" value={draft.cutoff} onChange={e => setDraft({ ...draft, cutoff: e.target.value })} placeholder="2026-11-02T12:00:00.123456Z"/></details><Button variant="secondary" type="submit">Preview period</Button></form>
    <section className="coach-notice" aria-labelledby="coach-privacy"><p className="eyebrow">Your progress, thoughtfully reviewed</p><h2 id="coach-privacy">A little context. You stay in control.</h2><p>Analysis starts only when you click. Groq receives limited numerical evidence and anonymous candidate identifiers. Personal goal titles, task descriptions, notes and credentials are not sent in the analysis data. Your records are never changed automatically.</p><p>Availability depends on backend configuration and free-tier limits. Provider retention depends on its account settings; zero retention has not been verified.</p><label className="coach-consent"><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)}/> I understand this sharing notice and want to request analysis.</label>
    {availability.isPending && <Status>Checking Coach availability…</Status>}{availability.isError && <Status error>Coach availability could not be checked. No analysis was sent.</Status>}{availability.data === 'NOT_CONFIGURED' && <Status>AI Coach is not configured. You can still review verified progress below.</Status>}
    <div className="actions"><Button disabled={pending || !consent || availability.data !== 'AVAILABLE' || !report.data || report.isFetching || report.isError || !!attempt} onClick={() => { void submit(false); }}>{pending ? 'Analyzing your progress…' : 'Analyze my progress'}</Button>
    {attempt && !canReplace && <Button variant="secondary" disabled={pending || !consent || availability.data !== 'AVAILABLE'} onClick={() => { void submit(true); }}>Recover or replay same analysis</Button>}
    <Button variant="secondary" disabled={pending} onClick={() => { void availability.refetch(); }}>Check availability</Button></div>
    {attempt && <p className="hint">Recorded submission: week of {attempt.body.weekStart}, cutoff {attempt.body.cutoff}. Recovery keeps the same submission ID. Closing the page or changing the preview may stop the browser wait, but may not stop provider processing.</p>}
    {(result || canReplace) && <Button variant="secondary" disabled={pending} onClick={() => { remember(undefined);setError('');setCanReplace(false); }}>Prepare a new analysis</Button>}
    </section>
    {pending && <Status>Analyzing once… You may continue using the app. No automatic retry will occur.</Status>}{error && <Status error>{error}</Status>}
    {report.isFetching && <Status>Loading verified preview…</Status>}{report.isError && <><Status error>{reportError(report.error)}</Status><Button variant="secondary" disabled={report.isFetching} onClick={() => { void report.refetch(); }}>Retry preview</Button></>}
    {result ? <section aria-labelledby="coach-result" aria-busy={pending}><h2 id="coach-result" ref={resultHeading} tabIndex={-1}>{result.source === 'AI_PRIORITIZED' ? 'Your focus, grounded in progress' : 'No eligible recommendations in this period'}</h2><p>{result.source === 'AI_PRIORITIZED' ? 'AI prioritized existing actions. Explanations and evidence come from the server.' : 'This is a rules-only result. No AI provider was called. Missing data is not failure.'}</p><WeekInfo value={result.evidence.period}/><p>{result.evidence.period.reportStatus === 'PROVISIONAL' ? 'In progress · provisional' : 'Closed · not finalized'} · {result.evidence.period.calendarStatus.toLowerCase()} calendar</p><p className="hint">Exclusive cutoff {result.evidence.period.cutoff}. Prepared {result.preparedAt}. Replay expires {result.expiresAt}. This result retains its original evidence when your records change.</p>
    {result.evidence.coverage.truncated && <Status>Partial goal coverage: {result.evidence.coverage.includedGoals} of {result.evidence.coverage.totalGoals} goals. These are the first goals in server creation order, not a ranking of every goal.</Status>}
    <div className="coach-facts">{result.evidence.facts.map(f => <article key={f.id}><h3>{f.id === 'tasks' ? 'Task completion' : f.id === 'habits' ? 'Habit consistency' : `Goal evidence ${f.id}`}</h3><p>{f.actual}{f.target === null ? ' units recorded · no weekly target' : ` / ${f.target}`}</p><strong>{f.percentage === null ? 'No percentage available' : `${f.percentage}%`}</strong></article>)}</div>
    <ol className="coach-cards">{result.selectedIds.map((id, index) => { const c = result.evidence.candidates.find(a => a.id === id)!; return <li key={id}><p className="eyebrow">Priority {index + 1}</p><h3>{c.title}</h3><p>{c.explanationTemplate}</p><p><strong>Next action:</strong> {c.nextAction}</p><ul>{c.evidenceIds.map(e => { const f = result.evidence.facts.find(a => a.id === e)!;return <li key={e}>Evidence {e}: {f.actual}{f.target === null ? ' recorded; no target' : ` / ${f.target}`}{f.percentage === null ? '' : ` · ${f.percentage}%`}</li>; })}</ul><Link className="button button-secondary" to={destinations[c.destination]}>Review {c.destination.toLowerCase()} ↗</Link></li>; })}</ol><p className="hint">One period cannot establish trends or motivation. Task cohorts retain original obligations; a habit day succeeds only when all non-rest obligations are complete. Historical suggestions link to current planning, not edits to a closed week.</p></section>
    : report.data && <section aria-labelledby="coach-preview"><h2 id="coach-preview">Verified progress preview</h2><WeekInfo value={report.data.period}/><p>{report.data.period.reportStatus === 'PROVISIONAL' ? 'In progress · provisional' : 'Closed · not finalized'} · {report.data.period.calendarStatus.toLowerCase()} calendar</p><p className="hint">Analysis will use week {report.data.period.weekStart} and exclusive cutoff {report.data.period.cutoff}. Percentages are calculated by the backend.</p><div className="coach-facts"><article><h3>Task completion</h3><strong>{report.data.tasks.percentage === null ? 'No assigned tasks' : `${report.data.tasks.percentage}%`}</strong><p>{report.data.tasks.completed} of {report.data.tasks.scheduled} tasks</p></article><article><h3>Habit consistency</h3><strong>{report.data.habits.percentage === null ? 'No scheduled activity' : `${report.data.habits.percentage}%`}</strong><p>{report.data.habits.successfulDays} of {report.data.habits.eligibleDays} eligible days</p></article></div><h3>Weekly goal progress</h3><p className="hint">Goal names and units are current metadata; recorded progress uses the selected historical cutoff.</p>{report.data.goals.totalElements === '0' && <p>No goals in this period. <Link to="/app/goals">Plan a measurable goal</Link>.</p>}{report.data.goals.items.map(g => <p key={g.goalId}>{g.title}: {g.completedUnits}{g.plannedUnits === null ? ' units recorded · no weekly target' : ` / ${g.plannedUnits}`} {g.unit} · {g.percentage === null ? 'No percentage available' : `${g.percentage}%`}</p>)}{BigInt(report.data.goals.totalElements) > 20n && <Status>Partial goal coverage: first 20 of {report.data.goals.totalElements} goals. Task and habit aggregates cover the period.</Status>}</section>}
  </div>;
}
