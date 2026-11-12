import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
export interface Profile { id: string; email: string; displayName: string; timezone: string }
function profile(data: unknown): Profile {
  if (!data || typeof data !== 'object' || !('id' in data) || !('email' in data) || !('displayName' in data) || !('timezone' in data) || typeof data.id !== 'string' || typeof data.email !== 'string' || typeof data.displayName !== 'string' || typeof data.timezone !== 'string') throw new Error('Invalid session response.');
  return { id: data.id, email: data.email, displayName: data.displayName, timezone: data.timezone };
}
export function useSession() { return useQuery({ queryKey: ['session'], queryFn: ({ signal }) => api.request('/me', profile, { signal }), retry: false, staleTime: 0 }); }
