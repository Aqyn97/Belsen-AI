import { useEffect, useState } from 'react';
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { useProductAction, type Outcome } from '../../shared/product/data';
import { history, type HistoryRange, type History } from './model';
const locks = new WeakMap<QueryClient, Set<string>>();
export const habitKey = (owner: string, id: string) => ['habits', owner, 'detail', id] as const;
export const versionKey = (owner: string, id: string) => ['habits', owner, 'version', id] as const;
export function rememberVersion(client: QueryClient, owner: string, id: string, value: string) {
    if (client.getQueryData<{
        id: string;
    } | null>(['session'])?.id !== owner)
        return;
    client.setQueryData<string>(versionKey(owner, id), old => old && BigInt(old) > BigInt(value) ? old : value);
}
export function useHabitVersion(owner: string, id: string, observed?: string) {
    const client = useQueryClient();
    const query = useQuery<string | null>({ queryKey: versionKey(owner, id), queryFn: () => null, enabled: false, initialData: null });
    useEffect(() => { if (observed)
        rememberVersion(client, owner, id, observed); }, [client, owner, id, observed]);
    return observed && (!query.data || BigInt(observed) > BigInt(query.data)) ? observed : query.data ?? observed ?? '0';
}
export interface HistoryPages {
    page: number;
    actionPage: number;
    schedulePage: number;
    size: number;
}
export const firstPages: HistoryPages = { page: 0, actionPage: 0, schedulePage: 0, size: 10 };
export function useHistory(owner: string, id: string, range: HistoryRange, pages: HistoryPages = firstPages, enabled = true) {
    const params = new URLSearchParams({ from: range.from, to: range.to, page: String(pages.page), size: String(pages.size), actionPage: String(pages.actionPage), schedulePage: String(pages.schedulePage) });
    if (range.cutoff)
        params.set('cutoff', range.cutoff);
    return useQuery({ queryKey: ['habits', owner, 'history', id, params.toString()], enabled, queryFn: ({ signal }) => api.request(`/habits/${id}/history?${params}`, history, { signal }) });
}
export function useHabitAction(owner: string) {
    const client = useQueryClient();
    const action = useProductAction(owner);
    const [notice, setNotice] = useState('');
    async function run<T extends {
        version: string;
    }>(id: string, expected: string, work: () => Promise<T>, accept?: (value: T) => void): Promise<Outcome<T>> {
        setNotice('');
        let held = locks.get(client);
        if (!held) {
            held = new Set();
            locks.set(client, held);
        }
        const key = `${owner}/${id}`;
        const latest = client.getQueryData<string>(versionKey(owner, id));
        if (action.isBusy() || held.has(key)) {
            setNotice('Another habit action is pending. Wait for confirmation before trying again.');
            return { ok: false, error: new Error('Habit action pending.') };
        }
        if (latest && BigInt(expected) < BigInt(latest)) {
            setNotice('This view has an older habit version. Refresh and review the latest state before retrying.');
            await action.refresh();
            return { ok: false, error: new Error('Stale habit view.') };
        }
        held.add(key);
        try {
            return await action.run(work, value => { rememberVersion(client, owner, id, value.version); accept?.(value); });
        }
        finally {
            held.delete(key);
        }
    }
    return { ...action, runHabit: run, notice };
}
export type HabitAction = ReturnType<typeof useHabitAction>;
export function latestHistoryVersion(value: History | undefined): string | undefined { return value?.days.items.reduce<string | undefined>((latest, d) => !latest || BigInt(d.version) > BigInt(latest) ? d.version : latest, undefined); }
