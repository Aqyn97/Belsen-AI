import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import { period } from './contracts';
import { useSession } from '../../features/auth/session';
export const entityKey = (kind: 'goals' | 'tasks' | 'habits', owner: string, id: string) => [kind, owner, 'detail', id] as const;
const kinds = new Set(['goals', 'tasks', 'habits', 'reporting', 'dashboard', 'analytics']);
export function useOwner() { return useSession().data?.id ?? ''; }
export function useReporting(owner: string) { return useQuery({ queryKey: ['reporting', owner], queryFn: ({ signal }) => api.request('/analytics/dashboard?goalsSize=1', period, { signal }), staleTime: 0 }); }
export function putEntity<T extends {id:string;version:string}>(client: QueryClient, kind: 'goals' | 'tasks' | 'habits', owner: string, value: T) {
    client.setQueryData<T>(entityKey(kind, owner, value.id), old => old && BigInt(old.version) > BigInt(value.version) ? old : value);
}
export function productMessage(error: unknown, mutation = false) {
    if (error instanceof ApiError) {
        switch (error.problem.status) {
            case 400: return 'The server rejected these values. Review the fields and reporting dates.';
            case 401: return 'Your session ended. Please sign in again.';
            case 403: return 'The action was forbidden. Your session is being checked. Nothing was automatically retried.';
            case 404: return 'This private resource is unavailable.';
            case 409: return 'The server could not accept this change. The latest state has been requested; your draft is preserved. Review versions, archive state and reporting-week rules before trying again.';
            case 429: return 'Too many requests. Wait before trying again.';
        }
    }
    return mutation ? 'The result is uncertain because the response was not confirmed. Refresh to check server state before repeating a non-idempotent action.' : 'Could not load the latest data. Check your connection and refresh.';
}
export type Outcome<T> = {
    ok: true;
    value: T;
} | {
    ok: false;
    error: unknown;
};
export function useProductAction(owner: string) {
    const client = useQueryClient();
    const busy = useRef(false);
    const [pending, setPending] = useState(false);
    const [message, setMessage] = useState('');
    const [failed, setFailed] = useState(false);
    const mutation = useMutation({ mutationFn: (work: () => Promise<unknown>) => work(), retry: false });
    async function refresh() {
        await client.invalidateQueries({ predicate: q => kinds.has(String(q.queryKey[0])) && q.queryKey[1] === owner });
    }
    async function run<T>(work: () => Promise<T>, accept?: (value: T) => void): Promise<Outcome<T>> {
        if (busy.current)
            return { ok: false, error: new Error('An action is already pending.') };
        busy.current = true;
        setPending(true);
        setMessage('');
        try {
            await client.cancelQueries({ predicate: q => kinds.has(String(q.queryKey[0])) && q.queryKey[1] === owner });
            const result = await mutation.mutateAsync(work) as T;
            if (client.getQueryData<{
                id: string;
            } | null>(['session'])?.id !== owner)
                throw new DOMException('Session changed.', 'AbortError');
            accept?.(result);
            await refresh();
            setFailed(false);
            setMessage('Saved. Refresh any section that shows an error.');
            return { ok: true, value: result };
        }
        catch (error) {
            if (error instanceof ApiError && error.problem.status === 409)
                await refresh();
            if (error instanceof ApiError && error.problem.status === 403)
                await client.invalidateQueries({ queryKey: ['session'] });
            setFailed(true);
            setMessage(productMessage(error, true));
            return { ok: false, error };
        }
        finally {
            busy.current = false;
            setPending(false);
        }
    }
    return { pending, message, failed, run, refresh, isBusy: () => busy.current, clear: () => setMessage('') };
}
export type Action = ReturnType<typeof useProductAction>;
export function serverFields(error: unknown) { return error instanceof ApiError && error.problem.status === 400 ? error.problem.fieldErrors ?? {} : {}; }
