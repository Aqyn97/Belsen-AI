import { useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, api } from '../../shared/api/client';
import { Button, Field, Loading, Status } from '../../shared/ui/components';
import { establishSession, failureMessage, passwordError, profile, safeReturn, timezoneError, useSession } from './session';

export function AuthPage() {
  const session = useSession(); const location = useLocation(); const navigate = useNavigate(); const client = useQueryClient();
  const [register, setRegister] = useState(false); const [visible, setVisible] = useState(false); const [pending, setPending] = useState(false);
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [name, setName] = useState('');
  const [zone, setZone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'); const [confirmed, setConfirmed] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({}); const [message, setMessage] = useState(''); const summary = useRef<HTMLDivElement>(null);
  const busy = useRef(false);
  function report(message: string, fields: Record<string, string> = {}) { setErrors(fields); setMessage(message); requestAnimationFrame(() => summary.current?.focus()); }
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy.current) return;
    const fields: Record<string, string> = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 254) fields.email = 'Enter a valid email address.';
    if (!password) fields.password = 'Enter your password.';
    if (register) {
      const invalidPassword = passwordError(password); if (invalidPassword) fields.password = invalidPassword;
      if (!name.trim() || name.trim().length > 100) fields.displayName = 'Use a name between 1 and 100 characters.';
      const invalidZone = timezoneError(zone); if (invalidZone) fields.timezone = invalidZone;
      if (!confirmed) fields.confirmation = 'Confirm your reporting timezone.';
    }
    if (Object.keys(fields).length) { report('Please review the form.', fields); return; }
    busy.current = true; setPending(true); setMessage(''); setErrors({});
    try {
      await api.request(register ? '/auth/register' : '/auth/login', profile, { method: 'POST', body: register ? { email: email.trim(), password, displayName: name.trim(), timezone: zone } : { email: email.trim(), password } });
      setPassword('');
      await establishSession(client);
      void navigate(safeReturn((location.state as { returnTo?: unknown } | null)?.returnTo), { replace: true });
    } catch (error) {
      report(failureMessage(error), error instanceof ApiError && error.problem.status === 400 ? error.problem.fieldErrors : undefined);
      if (error instanceof ApiError && error.problem.status === 403) { try { await api.bootstrap(); await session.refetch(); } catch { /* Explicit retry remains available. */ } }
      // A rotated session may exist even if the response/bootstrap was interrupted. Reconcile safely.
      if (!(error instanceof ApiError)) { api.clear(); void session.refetch(); }
    } finally { busy.current = false; setPending(false); }
  }
  if (session.isPending) return <main id="main" className="page"><Loading/></main>;
  if (session.data) return <Navigate to={safeReturn((location.state as { returnTo?: unknown } | null)?.returnTo)} replace/>;
  return <main id="main" tabIndex={-1} className="page auth-page"><Link className="brand" to="/">belsen<span>.</span></Link><p className="eyebrow">Your next chapter</p><h1>{register ? 'Make a fresh start.' : 'Welcome back.'}</h1><p>Small steps. A clearer direction.</p>
    {session.isError && <Status error>We could not verify your session. <Button onClick={() => { void session.refetch(); }}>Check connection</Button></Status>}
    {new URLSearchParams(location.search).get('error') === 'google' && <Status error>Google sign-in could not be completed. Use your original sign-in method or try again.</Status>}
    <div ref={summary} tabIndex={-1}>{message && <Status error>{message}</Status>}</div>
    <form onSubmit={event => { void submit(event); }} noValidate aria-label={register ? 'Registration' : 'Login'}>
      <fieldset disabled={pending || session.isError}><legend className="sr-only">{register ? 'Create account' : 'Sign in'}</legend>
        <Field label="Email" name="email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} error={errors.email}/>
        {register && <Field label="Display name" autoComplete="name" required maxLength={100} value={name} onChange={e => setName(e.target.value)} error={errors.displayName}/>}
        <Field label="Password" name="password" type={visible ? 'text' : 'password'} autoComplete={register ? 'new-password' : 'current-password'} required value={password} onChange={e => setPassword(e.target.value)} error={errors.password} aria-describedby={register ? 'password-rules' : undefined}/>
        <Button variant="secondary" aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? 'Hide password' : 'Show password'}</Button>
        {register && <><p id="password-rules" className="hint">At least 12 Unicode characters, at most 72 UTF-8 bytes. Spaces are preserved.</p><Field label="Reporting timezone" required list="timezones" value={zone} onChange={e => { setZone(e.target.value); setConfirmed(false); }} error={errors.timezone}/><TimezoneOptions/><label className="check"><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} aria-describedby={errors.confirmation ? 'confirmation-error' : undefined}/> I confirm {zone} as my reporting timezone.</label>{errors.confirmation && <p id="confirmation-error" className="field-error">{errors.confirmation}</p>}</>}
        <Button type="submit" disabled={pending}>{pending ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</Button>
      </fieldset>
    </form>
    <Button variant="secondary" disabled={pending} onClick={() => { setRegister(!register); setPassword(''); setMessage(''); setErrors({}); }}>{register ? 'Already have an account? Sign in' : 'New here? Create an account'}</Button>
    {import.meta.env.VITE_GOOGLE_ENABLED === 'true' && <a className="button button-secondary" href="/oauth2/authorization/google">Continue with Google</a>}
    <Link className="text-link" to="/">Back to home</Link>
  </main>;
}
export function TimezoneOptions() { return <datalist id="timezones">{Intl.supportedValuesOf('timeZone').concat('UTC').map(zone => <option key={zone} value={zone}/>)}</datalist>; }
export function AuthCallback() {
  const session = useSession(true, true);
  if (session.isPending || session.isFetching) return <main id="main" className="page"><Loading/></main>;
  if (session.data) return <Navigate to="/app/dashboard" replace/>;
  return <main id="main" className="page"><h1>Sign-in could not be completed</h1><Status error>We could not restore your account securely. Please sign in again.</Status><Link className="button button-primary" to="/auth">Return to sign in</Link></main>;
}
