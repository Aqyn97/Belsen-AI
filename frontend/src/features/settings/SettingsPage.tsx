import { useRef, useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { ApiError, api } from '../../shared/api/client';
import { Button, Field, Status } from '../../shared/ui/components';
import { failureMessage, profile, timezoneError, useSession, type Profile } from '../auth/session';
import { TimezoneOptions } from '../auth/AuthPage';
export function SettingsPage() {
  const session = useSession();
  return session.data ? <SettingsForm key={session.data.id} user={session.data}/> : null;
}
function SettingsForm({ user }: { user: Profile }) {
  const client = useQueryClient(); const navigate = useNavigate();
  const [name, setName] = useState(user.displayName); const [zone, setZone] = useState(user.timezone);
  const [pending, setPending] = useState(false); const [message, setMessage] = useState(''); const [failed, setFailed] = useState(false);
  const [errors, setErrors] = useState<Record<string,string>>({}); const summary = useRef<HTMLDivElement>(null); const busy = useRef(false);
  function feedback(text: string, error: boolean) { setMessage(text); setFailed(error); requestAnimationFrame(() => summary.current?.focus()); }
  async function save(event: FormEvent) {
    event.preventDefault(); if (busy.current) return;
    const fields: Record<string,string> = {}; if (!name.trim() || name.trim().length > 100) fields.displayName = 'Use a name between 1 and 100 characters.';
    const invalidZone = timezoneError(zone); if (invalidZone) fields.timezone = invalidZone;
    setErrors(fields); if (Object.keys(fields).length) { feedback('Please review your settings.', true); return; }
    busy.current = true; setPending(true); setMessage('');
    try {
      const updated = await api.request('/me', profile, { method: 'PATCH', body: { displayName: name.trim(), timezone: zone } });
      await client.cancelQueries();
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' });
      client.setQueryData(['session'], updated); setName(updated.displayName); setZone(updated.timezone); feedback('Settings saved. Reporting weeks keep their stored timezone; changes apply under the server calendar policy.', false);
    } catch (error) {
      setErrors(error instanceof ApiError && error.problem.status === 400 ? error.problem.fieldErrors ?? {} : {}); feedback(failureMessage(error), true);
      if (error instanceof ApiError && error.problem.status === 403) {
        // Reconcile identity too: a different tab may have changed the session.
        await client.invalidateQueries({ queryKey: ['session'] });
      }
    } finally { busy.current = false; setPending(false); }
  }
  async function logout() {
    if (busy.current) return; busy.current = true; setPending(true); setMessage('');
    await client.cancelQueries(); api.clear();
    try {
      await api.request('/auth/logout', () => undefined, { method: 'POST' });
      api.clear(); await client.cancelQueries(); client.clear(); client.setQueryData(['session'], null);
      void navigate('/auth', { replace: true });
    } catch (error) { feedback(failureMessage(error), true); }
    finally { busy.current = false; setPending(false); }
  }
  return <><header className="page-heading"><p className="eyebrow">Your account</p><h1 id="page-title" tabIndex={-1}>Settings</h1><p>Keep the essentials up to date.</p></header><section className="settings-card"><h2>Profile & reporting</h2><p>Current reporting timezone: <strong>{user.timezone}</strong></p><div ref={summary} tabIndex={-1}>{message && <Status error={failed}>{message}</Status>}</div><form onSubmit={event => { void save(event); }} noValidate><fieldset disabled={pending}><legend className="sr-only">Account settings</legend><Field label="Email" value={user.email} readOnly autoComplete="email"/><Field label="Display name" value={name} onChange={e => setName(e.target.value)} error={errors.displayName} required maxLength={100} autoComplete="name"/><Field label="Reporting timezone" list="timezones" value={zone} onChange={e => setZone(e.target.value)} error={errors.timezone} required aria-describedby="timezone-policy"/><TimezoneOptions/><p id="timezone-policy" className="hint">Existing reporting weeks retain their timezone. Changes apply prospectively and may conflict with reserved future weeks. The server determines reporting boundaries.</p><Button type="submit">{pending ? 'Please wait…' : 'Save settings'}</Button></fieldset></form></section><section className="settings-card"><h2>End this session</h2><p>Sign out on this browser when you are done.</p><Button variant="secondary" disabled={pending} onClick={() => { void logout(); }}>Sign out</Button></section></>;
}
