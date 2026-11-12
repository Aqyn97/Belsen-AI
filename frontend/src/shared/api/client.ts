import { parseExact, stringifyExact } from '../lib/numbers';
export interface Problem { status: number; title?: string; type?: string; instance?: string; detail?: string; requestId?: string; fieldErrors?: Record<string, string> }
export class ApiError extends Error {
  constructor(public readonly problem: Problem, public readonly retryAfter: string | null = null) { super(problem.detail ?? 'The request could not be completed.'); }
}
export class NetworkError extends Error { constructor() { super('Unable to reach the server. Check your connection and try again.'); } }
export const sessionEvents = new EventTarget();
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
export function createApiClient(fetcher: typeof fetch = (...args) => fetch(...args)) {
  let token: { headerName: string; token: string } | undefined;
  let flight: Promise<void> | undefined; let generation = 0;
  function clear() { generation++; token = undefined; flight = undefined; }
  async function send(path: string, init: RequestInit): Promise<Response> {
    if (!/^\/[A-Za-z0-9][A-Za-z0-9/_-]*$/.test(path.split('?')[0] ?? '') || path.startsWith('//') || path.includes('\\') || path.includes('#') || path.includes('..')) throw new Error('Use a relative API resource path.');
    try { return await fetcher(`/api/v1${path}`, { ...init, credentials: 'same-origin', redirect: 'error' }); }
    catch (error) { if (init.signal?.aborted || (error instanceof DOMException && error.name === 'AbortError')) throw error; throw new NetworkError(); }
  }
  async function body(response: Response): Promise<unknown> {
    if (response.status === 204) return undefined;
    try { return parseExact(await response.text()); } catch { throw new ApiError({ status: response.status, detail: 'The server returned an unreadable response.' }); }
  }
  async function checked(response: Response, epoch: number): Promise<unknown> {
    if (epoch !== generation) throw new DOMException('Session changed.', 'AbortError');
    if (response.status === 401) { clear(); sessionEvents.dispatchEvent(new Event('expired')); }
    const observed = generation;
    const data = await body(response);
    if (observed !== generation) throw new DOMException('Session changed.', 'AbortError');
    if (!response.ok) {
      // Status comes from HTTP; never trust a numeric value embedded in an error body.
      const problem: Problem = { status: response.status };
      if (object(data)) {
        if (typeof data.title === 'string') problem.title = data.title;
        if (typeof data.type === 'string') problem.type = data.type;
        if (typeof data.instance === 'string') problem.instance = data.instance;
        if (typeof data.detail === 'string') problem.detail = data.detail;
        if (typeof data.requestId === 'string') problem.requestId = data.requestId;
        if (object(data.fieldErrors)) problem.fieldErrors = Object.fromEntries(Object.entries(data.fieldErrors).filter((entry): entry is [string,string] => typeof entry[1] === 'string'));
      }
      if (response.status === 403) clear();
      throw new ApiError(problem, response.status === 429 ? response.headers.get('Retry-After') : null);
    }
    return data;
  }
  async function bootstrap(): Promise<void> {
    if (token) return;
    if (!flight) {
      const epoch = generation;
      flight = (async () => {
        const data = await checked(await send('/auth/csrf', { method: 'GET', headers: { Accept: 'application/json' } }), epoch);
        if (!object(data) || typeof data.headerName !== 'string' || typeof data.token !== 'string' || !/^X-[A-Za-z0-9-]+$/i.test(data.headerName) || !data.token) throw new Error('Invalid CSRF bootstrap response.');
        if (epoch !== generation) throw new DOMException('Session changed.', 'AbortError');
        token = { headerName: data.headerName, token: data.token };
      })().finally(() => { if (epoch === generation) flight = undefined; });
    }
    await flight;
  }
  async function request<T>(path: string, decode: (data: unknown) => T, options: { method?: 'GET'|'POST'|'PATCH'|'PUT'|'DELETE'; body?: unknown; signal?: AbortSignal; ifMatch?: string } = {}): Promise<T> {
    const method = options.method ?? 'GET'; const epoch = generation;
    if (options.signal?.aborted) throw new DOMException('Request aborted.', 'AbortError');
    if (method !== 'GET') await bootstrap();
    if (epoch !== generation || options.signal?.aborted) throw new DOMException('Session changed or request aborted.', 'AbortError');
    const headers = new Headers({ Accept: 'application/json' });
    if (method !== 'GET' && token) headers.set(token.headerName, token.token);
    if (options.ifMatch !== undefined) {
      if (!/^"(0|[1-9]\d*)"$/.test(options.ifMatch)) throw new Error('Use one strong quoted If-Match version.');
      headers.set('If-Match', options.ifMatch);
    }
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    const result = await checked(await send(path, { method, headers, signal: options.signal, body: options.body === undefined ? undefined : stringifyExact(options.body) }), epoch);
    if (epoch !== generation) throw new DOMException('Session changed.', 'AbortError');
    return decode(result);
  }
  return { request, bootstrap, clear, async afterAuthentication() { clear(); await bootstrap(); } };
}
export const api = createApiClient();
