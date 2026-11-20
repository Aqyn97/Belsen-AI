import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError, api } from '../../shared/api/client';
export interface Profile { id: string; email: string; displayName: string; timezone: string }
export function profile(data: unknown): Profile {
  if (!data || typeof data !== 'object' || !('id' in data) || !('email' in data) || !('displayName' in data) || !('timezone' in data) || typeof data.id !== 'string' || typeof data.email !== 'string' || typeof data.displayName !== 'string' || typeof data.timezone !== 'string') throw new Error('Invalid session response.');
  return { id: data.id, email: data.email, displayName: data.displayName, timezone: data.timezone };
}
export function useSession(enabled = true, callback = false) {
  const client = useQueryClient();
  return useQuery({ queryKey: ['session'], enabled, queryFn: async ({ signal }) => {
    try {
      if (callback) await api.afterAuthentication(); else await api.bootstrap();
      const user = await api.request('/me', profile, { signal });
      const previous = client.getQueryData<Profile | null>(['session']);
      if (previous?.id !== user.id) {
        // Cookies can change in another tab. Never reuse the previous owner's cache.
        await client.cancelQueries({ predicate: query => query.queryKey[0] !== 'session' });
        client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' });
        if (previous) await api.afterAuthentication();
      }
      return user;
    } catch (error) { if (error instanceof ApiError && error.problem.status === 401) return null; throw error; }
  }, retry: false, staleTime: callback ? 0 : Infinity, refetchOnMount: callback ? 'always' : true, refetchOnWindowFocus: callback ? false : 'always' });
}
export async function establishSession(client: QueryClient) {
  await client.cancelQueries();
  client.clear();
  await api.afterAuthentication();
  const user = await api.request('/me', profile);
  client.setQueryData(['session'], user);
}
export const safeReturn = (value: unknown) => typeof value === 'string' && /^\/app\/(dashboard|goals|tasks|habits|analytics|coach|settings)$/.test(value) ? value : '/app/dashboard';
export function passwordError(value: string) {
  if ([...value].length < 12) return 'Use at least 12 Unicode characters.';
  if (new TextEncoder().encode(value).length > 72) return 'Use at most 72 UTF-8 bytes (some characters use more than one byte).';
  if (!value.trim()) return 'Choose a password with a non-space character.';
  return undefined;
}
export function timezoneError(value: string) {
  try { if (!value || /^[+-]/.test(value)) return 'Enter an IANA timezone, such as Asia/Almaty.'; new Intl.DateTimeFormat('en', { timeZone: value }); return undefined; }
  catch { return 'Enter an IANA timezone, such as Asia/Almaty.'; }
}
export function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.problem.status) {
      case 400: return 'Check the highlighted fields and try again.';
      case 401: return 'Sign-in could not be completed. Check your credentials and try again.';
      case 403: return 'Your security token needs refreshing or this action is forbidden. Nothing was retried. Check your session, then submit again.';
      case 409: return 'This change conflicts with existing account or reporting-calendar data. For timezone changes, a reserved future week may prevent the change. Keep your current settings or try again later.';
      case 429: return `Too many attempts. Try again ${error.retryAfter && /^\d+$/.test(error.retryAfter) ? `after ${error.retryAfter} seconds` : 'later'}.`;
    }
  }
  return 'Unable to complete the request. Check your connection and try again.';
}
