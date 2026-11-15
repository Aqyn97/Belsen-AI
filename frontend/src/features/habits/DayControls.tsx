import { useState } from 'react';
import { api } from '../../shared/api/client';
import { version } from '../../shared/lib/numbers';
import { Button, Field, Status } from '../../shared/ui/components';
import { ConfirmDialog, Feedback } from '../../shared/product/ui';
import { day, dayLabel, dayPermissions, type Day, type History } from './model';
import { useHabitVersion, type HabitAction } from './data';
export function DayControls({ owner, id, value, snapshot, action, allowRest = false, allowDaily = true, disabled = false }: {
    owner: string;
    id: string;
    value: Day;
    snapshot: History;
    action: HabitAction;
    allowRest?: boolean;
    allowDaily?: boolean;
    disabled?: boolean;
}) {
    const latest = useHabitVersion(owner, id, value.version);
    const permissions = dayPermissions(value, snapshot, latest);
    const [reason, setReason] = useState('');
    const [error, setError] = useState('');
    const [confirmed, setConfirmed] = useState<Day | null>(null);
    const [confirmation, setConfirmation] = useState<{
        verb: 'rest' | 'cancel';
        version: string;
        reason: string;
    } | null>(null);
    async function mutate(verb: 'complete' | 'undo' | 'rest' | 'cancel', expected = value.version, acceptedReason = reason) {
        if (disabled) {
            setConfirmation(null);
            setError('Refresh this reporting day before confirming an action. Your intent has been kept.');
            return;
        }
        const context = value.reportingDay;
        if (!context || snapshot.exclusiveCutoff || action.isBusy())
            return;
        const rest = verb === 'rest' || verb === 'cancel';
        const remove = verb === 'undo' || verb === 'cancel';
        const base = `/habits/${id}/${rest ? 'rest-days' : 'completions'}/${value.localDate}`;
        await action.runHabit(id, expected, () => api.request(remove ? `${base}?weekStart=${context.week.weekStart}` : base, day, { method: remove ? 'DELETE' : 'PUT', ...(remove ? { ifMatch: `"${version(expected).toString()}"` } : { body: { version: version(expected), weekStart: context.week.weekStart, ...(verb === 'rest' ? { reason: acceptedReason.trim() } : {}) } }) }), response => { setConfirmed(response); setReason(''); });
    }
    function askRest() { if (!reason.trim() || reason.trim().length > 500) {
        setError('Give a rest reason between 1 and 500 characters.');
        return;
    } setError(''); setConfirmation({ verb: 'rest', version: value.version, reason }); }
    return <div className="day-controls"><Feedback message={error}/>{!snapshot.exclusiveCutoff && confirmed && <Status>Last confirmed response for {confirmed.localDate}: {dayLabel(confirmed)}.</Status>}
  {value.version !== latest && !snapshot.exclusiveCutoff && <p className="hint">Another date or edit changed this habit. Refresh this view before acting.</p>}
  {!snapshot.exclusiveCutoff && <fieldset disabled={disabled || action.pending}><div className="actions">
   {allowDaily && permissions.complete && <Button onClick={() => { void mutate('complete'); }}>Complete {value.localDate}</Button>}
   {allowDaily && permissions.undo && <Button variant="secondary" onClick={() => { void mutate('undo'); }}>Undo completion {value.localDate}</Button>}
   {permissions.cancelRest && <Button variant="secondary" onClick={() => setConfirmation({ verb: 'cancel', version: value.version, reason: '' })}>Cancel rest {value.localDate}</Button>}
  </div>{allowRest && permissions.rest && <><Field label="Rest reason" maxLength={500} value={reason} onChange={e => setReason(e.target.value)}/><Button onClick={askRest}>Plan rest {value.localDate}</Button></>}</fieldset>}
  {confirmation && <ConfirmDialog title={confirmation.verb === 'rest' ? 'Plan a rest day?' : 'Cancel planned rest?'} pending={action.pending} onCancel={() => setConfirmation(null)} onConfirm={() => { const submitted = confirmation; setConfirmation(null); void mutate(submitted.verb, submitted.version, submitted.reason); }}>{confirmation.verb === 'rest' ? `Remove this habit's obligation on ${value.localDate}. Rest is not successful completion. Reason: ${confirmation.reason}` : `Restore any applicable scheduled obligation on ${value.localDate}. Cancellation must reach the server before that reporting day starts.`}</ConfirmDialog>}
 </div>;
}
