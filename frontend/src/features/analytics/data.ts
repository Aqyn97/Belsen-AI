import { useQuery } from '@tanstack/react-query';
import { api, NetworkError } from '../../shared/api/client';
import { dashboard, reportPath, type Selection } from './model';
export function useDashboard(owner: string, selection: Selection, page: number) {
  return useQuery({ queryKey: ['analytics', owner, 'dashboard', selection.weekStart, selection.cutoff, page],
    queryFn: ({ signal }) => api.request(reportPath(selection, page), dashboard, { signal }),
    enabled: !!owner, staleTime: 0, retryDelay: 300, retry: (attempt, error) => error instanceof NetworkError && attempt < 1 });
}
