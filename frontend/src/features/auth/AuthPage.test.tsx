import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { createQueryClient } from '../../app/providers';
import { api } from '../../shared/api/client';
const alice = {id:'alice',email:'alice@example.test',displayName:'Alice',timezone:'UTC'};
let authenticated: boolean; let posts: string[]; let csrfCount: number; let rejection: number;
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {status,headers:{'Retry-After':'900'}});
beforeEach(() => {
  api.clear(); authenticated = false; posts = []; csrfCount = 0; rejection = 0;
  vi.stubGlobal('fetch',vi.fn<typeof fetch>().mockImplementation(async (url, init) => {
    const path = String(url);
    if (path.endsWith('/csrf')) { csrfCount++; return json({headerName:'X-CSRF-TOKEN',token:`token-${csrfCount}`}); }
    if (init?.method === 'POST') {
      posts.push(path);
      if (rejection) return json({detail:'private provider diagnostic'}, rejection);
      authenticated = !path.endsWith('/logout');
      return authenticated ? json(alice) : new Response(null,{status:204});
    }
    if (init?.method === 'PATCH') return rejection ? json({},rejection) : json({...alice,...JSON.parse(String(init.body))});
    return authenticated ? json(alice) : json({},401);
  }));
});
afterEach(() => vi.unstubAllGlobals());
function show(path = '/auth') {
  const client = createQueryClient();
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><App/></MemoryRouter></QueryClientProvider>);
  return client;
}
async function login() {
  const user = userEvent.setup(); await screen.findByRole('heading',{name:'Welcome back.'});
  await user.type(screen.getByLabelText('Email'),'alice@example.test'); await user.type(screen.getByLabelText('Password'),'unchanged password'); await user.click(screen.getByRole('button',{name:'Sign in'})); return user;
}
it('logs in, renews CSRF and resumes a safe protected deep link', async () => { show('/app/tasks'); await login(); expect(await screen.findByRole('heading',{name:'Tasks'})).toBeVisible(); expect(csrfCount).toBeGreaterThanOrEqual(2); expect(posts).toEqual(['/api/v1/auth/login']); });
it('registers through the API only after explicit timezone confirmation', async () => {
  show(); const user = userEvent.setup(); await screen.findByRole('heading',{name:'Welcome back.'}); await user.click(screen.getByRole('button',{name:'New here? Create an account'}));
  await user.type(screen.getByLabelText('Email'),'alice@example.test'); await user.type(screen.getByLabelText('Display name'),'Alice'); await user.type(screen.getByLabelText('Password'),'correct horse battery');
  await user.click(screen.getByRole('button',{name:'Create account'})); expect(await screen.findByText('Confirm your reporting timezone.')).toBeVisible(); expect(posts).toHaveLength(0);
  await user.click(screen.getByRole('checkbox')); await user.click(screen.getByRole('button',{name:'Create account'})); expect(await screen.findByRole('navigation',{name:'Workspace'})).toBeVisible(); expect(posts).toEqual(['/api/v1/auth/register']);
});
it.each([401,429,403])('handles %s without replay or exposing diagnostics', async status => { rejection = status; show(); await login(); expect(await screen.findByRole('alert')).not.toHaveTextContent('diagnostic'); if (status === 429) expect(screen.getByRole('alert')).toHaveTextContent('900 seconds'); expect(posts).toHaveLength(1); });
it('hides Google when no deployment flag enables it', async () => { show(); await screen.findByRole('heading',{name:'Welcome back.'}); expect(screen.queryByRole('link',{name:'Continue with Google'})).not.toBeInTheDocument(); });
it('shows a fixed top-level Google link only with the deployment flag', async () => { vi.stubEnv('VITE_GOOGLE_ENABLED','true'); show(); expect(await screen.findByRole('link',{name:'Continue with Google'})).toHaveAttribute('href','/oauth2/authorization/google'); vi.unstubAllEnvs(); });
it('reports safe Google callback failures', async () => { show('/auth/callback'); expect(await screen.findByRole('heading',{name:'Sign-in could not be completed'})).toBeVisible(); });
it('restores the callback profile after refreshing CSRF', async () => { authenticated = true; show('/auth/callback'); expect(await screen.findByRole('heading',{name:'Dashboard'})).toBeVisible(); expect(csrfCount).toBe(1); });
it('clears private caches on logout and prevents account data leaking into the next session', async () => {
  authenticated = true; const client = show('/app/settings'); await screen.findByDisplayValue('Alice'); client.setQueryData(['goals','alice'], {private:'alice'});
  await userEvent.click(screen.getByRole('button',{name:'Sign out'})); expect(await screen.findByRole('heading',{name:'Welcome back.'})).toBeVisible(); expect(client.getQueryData(['goals','alice'])).toBeUndefined(); expect(client.getQueryData(['session'])).toBeNull();
});
it('updates real typed profile state and invalidates reporting caches', async () => {
  authenticated = true; const client = show('/app/settings'); const input = await screen.findByLabelText('Display name'); client.setQueryData(['analytics'],{old:true}); const user = userEvent.setup(); await user.clear(input); await user.type(input,'Alice Updated'); await user.click(screen.getByRole('button',{name:'Save settings'}));
  expect(await screen.findByRole('status')).toHaveTextContent('Settings saved'); expect(client.getQueryData(['analytics'])).toBeUndefined(); expect(client.getQueryData(['session'])).toMatchObject({displayName:'Alice Updated'});
});
it.each([403,409])('keeps settings and safely explains %s without replay', async status => { authenticated = true; rejection = status; show('/app/settings'); await screen.findByDisplayValue('Alice'); await userEvent.click(screen.getByRole('button',{name:'Save settings'})); expect(await screen.findByRole('alert')).toHaveTextContent(status === 409 ? 'reserved future week' : 'Nothing was retried'); expect(screen.getByLabelText('Display name')).toHaveValue('Alice'); });
it('does not treat network loss during logout as confirmed logout', async () => {
  authenticated = true; show('/app/settings'); await screen.findByDisplayValue('Alice'); vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new Error('offline'))); await userEvent.click(screen.getByRole('button',{name:'Sign out'})); expect(await screen.findByRole('alert')).toHaveTextContent('connection'); expect(screen.getByLabelText('Email')).toHaveValue(alice.email);
});
it('redirects an authenticated auth-page visitor to dashboard', async () => { authenticated = true; show(); await waitFor(() => expect(screen.getByRole('heading',{name:'Dashboard'})).toBeVisible()); });
it('drops private content when an active session receives 401', async () => {
  authenticated = true; const client = show('/app/settings'); await screen.findByDisplayValue('Alice'); client.setQueryData(['goals'],{private:'alice'});
  authenticated = false; await api.request('/me', data => data).catch(() => undefined);
  expect(await screen.findByRole('heading',{name:'Welcome back.'})).toBeVisible(); expect(client.getQueryData(['goals'])).toBeUndefined(); expect(screen.queryByDisplayValue('Alice')).not.toBeInTheDocument();
});

it('clears old-owner caches when session reconciliation discovers another account', async () => {
  authenticated = true; const client = show('/app/settings'); await screen.findByDisplayValue('Alice'); client.setQueryData(['goals'],{private:'alice'});
  vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockImplementation(async url => String(url).endsWith('/csrf') ? json({headerName:'X-CSRF-TOKEN',token:'bob-token'}) : json({...alice,id:'bob',displayName:'Bob',email:'bob@example.test'})));
  await client.invalidateQueries({queryKey:['session']});
  expect(await screen.findByDisplayValue('Bob')).toBeVisible(); expect(client.getQueryData(['goals'])).toBeUndefined();
});
it('reconciles an interrupted authentication response with fresh CSRF and the real profile', async () => {
  const client = show(); await screen.findByRole('heading',{name:'Welcome back.'}); client.setQueryData(['goals'],{private:'old-owner'});
  vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockImplementation(async (url, init) => {
    if (String(url).endsWith('/csrf')) { csrfCount++; return json({headerName:'X-CSRF-TOKEN',token:'fresh'}); }
    if (init?.method === 'POST') { authenticated = true; throw new Error('response lost'); }
    return json(alice);
  }));
  await login(); expect(await screen.findByRole('heading',{name:'Dashboard'})).toBeVisible(); expect(csrfCount).toBeGreaterThanOrEqual(2); expect(client.getQueryData(['goals'])).toBeUndefined();
});

it('reconciles identity after a forbidden settings update before permitting a manual retry', async () => {
  authenticated = true; show('/app/settings'); await screen.findByDisplayValue('Alice'); let patches = 0;
  vi.stubGlobal('fetch',vi.fn<typeof fetch>().mockImplementation(async (url,init) => {
    if (String(url).endsWith('/csrf')) return json({headerName:'X-CSRF-TOKEN',token:'new-owner'});
    if (init?.method === 'PATCH') { patches++; return json({},403); }
    return json({...alice,id:'bob',displayName:'Bob',email:'bob@example.test'});
  }));
  await userEvent.click(screen.getByRole('button',{name:'Save settings'}));
  expect(await screen.findByDisplayValue('Bob')).toBeVisible(); expect(screen.queryByDisplayValue('Alice')).not.toBeInTheDocument(); expect(patches).toBe(1);
});
