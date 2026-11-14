import { expect, it, vi } from 'vitest';
import { ApiError, createApiClient, NetworkError, sessionEvents } from './client';
import { version } from '../lib/numbers';
const response = (data: string, status = 200, headers = {}) => new Response(data, { status, headers });
const unknown = (data: unknown) => data;
it('constructs same-origin requests and forwards cancellation', async () => { const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response('{}')); const client = createApiClient(fetcher); const controller = new AbortController(); await client.request('/me', unknown, { signal: controller.signal }); expect(fetcher).toHaveBeenCalledWith('/api/v1/me', expect.objectContaining({ credentials: 'same-origin', signal: controller.signal, redirect: 'error' })); });
it('bootstraps once concurrently, uses dynamic CSRF header and exact versions', async () => { const fetcher = vi.fn<typeof fetch>().mockImplementation(async (url) => response(String(url).endsWith('/csrf') ? '{"headerName":"X-CUSTOM-CSRF","token":"first"}' : '{}')); const client = createApiClient(fetcher); await Promise.all([client.bootstrap(),client.bootstrap()]); await client.request('/tasks/x/archive', unknown, { method: 'POST', body: { version: version('9007199254740993') } }); expect(fetcher).toHaveBeenCalledTimes(2); const init = fetcher.mock.calls[1]?.[1]; expect(new Headers(init?.headers).get('X-CUSTOM-CSRF')).toBe('first'); expect(init?.body).toBe('{"version":9007199254740993}'); });
it('refreshes CSRF after authentication transition', async () => { let count = 0; const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => response(`{"headerName":"X-CSRF-TOKEN","token":"${++count}"}`)); const client = createApiClient(fetcher); await client.bootstrap(); await client.afterAuthentication(); expect(fetcher).toHaveBeenCalledTimes(2); });
it.each([403,409,429])('does not retry mutation on %s and retains problem metadata', async status => { const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(response('{"headerName":"X-CSRF-TOKEN","token":"a"}')).mockResolvedValueOnce(response('{"title":"Conflict","type":"about:blank","instance":"/api/v1/tasks","detail":"Request denied","requestId":"trace","fieldErrors":{"title":"Required"}}', status, { 'Retry-After': '900' })); const client = createApiClient(fetcher); const error = await client.request('/tasks', unknown, { method: 'POST', body: {} }).catch(e => e as ApiError); expect(error).toBeInstanceOf(ApiError); expect((error as ApiError).problem.status).toBe(status); expect((error as ApiError).problem.title).toBe('Conflict'); expect((error as ApiError).problem.instance).toBe('/api/v1/tasks'); expect((error as ApiError).retryAfter).toBe(status === 429 ? '900' : null); expect(fetcher).toHaveBeenCalledTimes(2); });
it('notifies session expiry and clears CSRF on 401', async () => { const listener = vi.fn(); sessionEvents.addEventListener('expired', listener); const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response('{}',401)); await expect(createApiClient(fetcher).request('/me',unknown)).rejects.toBeInstanceOf(ApiError); expect(listener).toHaveBeenCalledOnce(); sessionEvents.removeEventListener('expired',listener); });
it('handles no-content responses', async () => { const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null,{status:204})); expect(await createApiClient(fetcher).request('/me',unknown)).toBeUndefined(); });
it('sanitizes network errors without exposing credentials', async () => { const client = createApiClient(vi.fn<typeof fetch>().mockRejectedValue(new Error('private password'))); await expect(client.request('/me',unknown)).rejects.toBeInstanceOf(NetworkError); await expect(client.request('/me',unknown)).rejects.not.toThrow('private password'); });
it('does not start an already-aborted request', async () => { const fetcher = vi.fn<typeof fetch>(); const controller = new AbortController(); controller.abort(); await expect(createApiClient(fetcher).request('/me',unknown,{signal:controller.signal})).rejects.toHaveProperty('name','AbortError'); expect(fetcher).not.toHaveBeenCalled(); });
it('rejects cross-origin, traversal and invalid conditional headers', async () => { const client = createApiClient(vi.fn<typeof fetch>()); await expect(client.request('//evil.test',unknown)).rejects.toThrow(); await expect(client.request('/../other',unknown)).rejects.toThrow(); await expect(client.request('/me',unknown,{ifMatch:'*'})).rejects.toThrow(); });
it('does not allow an obsolete CSRF bootstrap to repopulate token state', async () => { let resolve!: (value: Response) => void; const fetcher = vi.fn<typeof fetch>().mockImplementation(() => new Promise<Response>(r => { resolve = r; })); const client = createApiClient(fetcher); const old = client.bootstrap(); client.clear(); resolve(response('{"headerName":"X-CSRF-TOKEN","token":"obsolete"}')); await expect(old).rejects.toHaveProperty('name','AbortError'); });

it('ignores a stale 401 arriving after authentication changed', async () => {
  let finish!: (value: Response) => void;
  const fetcher = vi.fn<typeof fetch>().mockImplementationOnce(() => new Promise<Response>(r => { finish = r; })).mockResolvedValue(response('{"headerName":"X-CSRF-TOKEN","token":"fresh"}'));
  const client = createApiClient(fetcher); const listener = vi.fn();sessionEvents.addEventListener('expired',listener);
  const old = client.request('/me',unknown);await client.afterAuthentication();finish(response('{}',401));
  await expect(old).rejects.toHaveProperty('name','AbortError');expect(listener).not.toHaveBeenCalled();sessionEvents.removeEventListener('expired',listener);
});

it('discards an old successful private response after logout clears the session', async () => {
  let finish!: (value: Response) => void;
  const fetcher = vi.fn<typeof fetch>().mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }));
  const client = createApiClient(fetcher); const old = client.request('/me', unknown);
  client.clear(); finish(response('{"id":"old-account"}'));
  await expect(old).rejects.toHaveProperty('name','AbortError');
});
it('uses a new token for an explicit retry after 403 without replaying the failed mutation', async () => {
  let csrf = 0; let mutations = 0;
  const fetcher = vi.fn<typeof fetch>().mockImplementation(async url => {
    if (String(url).endsWith('/csrf')) return response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:`token-${++csrf}`}));
    return response('{}',++mutations === 1 ? 403 : 200);
  });
  const client = createApiClient(fetcher);
  await expect(client.request('/me',unknown,{method:'PATCH',body:{}})).rejects.toBeInstanceOf(ApiError);
  expect(mutations).toBe(1); await client.bootstrap(); expect(mutations).toBe(1);
  await client.request('/me',unknown,{method:'PATCH',body:{}});
  expect(new Headers(fetcher.mock.calls.at(-1)?.[1]?.headers).get('X-CSRF-TOKEN')).toBe('token-2');
});
