import { useEffect, useId, useRef, useState, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Button, Status } from '../ui/components';
import { goal, pageOf, type Page, type Week } from './contracts';
import type { Errors } from './forms';
export function Select({ label, ...props }: SelectHTMLAttributes<HTMLSelectElement> & {
    label: string;
}) { const id = useId(); return <div className="field"><label htmlFor={id}>{label}</label><select {...props} id={id}/></div>; }
export function Textarea({ label, error, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & {
    label: string;
    error?: string;
}) { const id = useId(); return <div className="field"><label htmlFor={id}>{label}</label><textarea {...props} id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}/>{error && <span className="field-error" id={`${id}-error`}>{error}</span>}</div>; }
export function Feedback({ message, errors = {}, failed = true }: {
    message?: string;
    errors?: Errors;
    failed?: boolean;
}) { const ref = useRef<HTMLDivElement>(null); const contents = message || Object.values(errors).join(' '); useEffect(() => { if (contents)
    ref.current?.focus(); }, [contents]); return <div ref={ref} tabIndex={-1}>{contents && <Status error={failed}>{message || 'Please review the form.'}{Object.keys(errors).length > 0 && <ul>{Object.entries(errors).map(([key, value]) => <li key={key}>{value}</li>)}</ul>}</Status>}</div>; }
export function Pagination({ data, page, onPage, label = 'Results', disabled = false }: {
    data: Page<unknown>;
    page: number;
    onPage: (page: number) => void;
    label?: string;
    emptyLabel?: string;
    disabled?: boolean;
}) { const more = BigInt(page + 1) * BigInt(data.size) < BigInt(data.totalElements); return <nav className="pagination" aria-label={`${label} pagination`}><Button variant="secondary" disabled={disabled || page === 0} onClick={() => onPage(page - 1)}>Previous {label.toLowerCase()}</Button><span>Page {page + 1} · {data.totalElements} total</span><Button variant="secondary" disabled={disabled || !more || page >= 2147483647} onClick={() => onPage(page + 1)}>Next {label.toLowerCase()}</Button></nav>; }
export function Heading({ title, children }: {
    title: string;
    children?: ReactNode;
}) { return <header className="product-heading"><div><p className="eyebrow">A little direction. Every day.</p><h1 id="page-title" tabIndex={-1}>{title}</h1></div>{children}</header>; }
export function WeekInfo({ value }: {
    value: Week;
}) { return <span>Week of {value.weekStart} · {value.timezone}<small className="report-boundaries">{value.startsAt} — {value.endsAt} (end exclusive)</small></span>; }
export function ConfirmDialog({ title, children, pending, onCancel, onConfirm }: {
    title: string;
    children: ReactNode;
    pending: boolean;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    const ref = useRef<HTMLDialogElement>(null);
    const id = useId();
    useEffect(() => { const dialog = ref.current; const trigger = document.activeElement as HTMLElement | null; dialog?.showModal(); return () => { dialog?.close(); if (trigger?.isConnected) trigger.focus(); else document.querySelector<HTMLElement>('.detail-panel h2')?.focus(); }; }, []);
    useEffect(() => { document.getElementById('page-title')?.focus(); }, [title]);
    return <dialog ref={ref} aria-labelledby={id} onCancel={event => { event.preventDefault(); if (!pending)
        onCancel(); }}><h2 id={id}>{title}</h2><p>{children}</p><div className="actions"><Button autoFocus variant="secondary" disabled={pending} onClick={onCancel}>Cancel</Button><Button disabled={pending} onClick={onConfirm}>{pending ? 'Please wait…' : 'Confirm'}</Button></div></dialog>;
}
export function GoalPicker({ owner, value, onChange, activeOnly = false, label = 'Associated goal', emptyLabel = 'No goal', disabled = false }: {
    owner: string;
    value: string;
    onChange: (value: string) => void;
    activeOnly?: boolean;
    label?: string;
    emptyLabel?: string;
    disabled?: boolean;
}) {
    const [page, setPage] = useState(0);
    const query = useQuery({ queryKey: ['goals', owner, 'list', page], queryFn: ({ signal }) => api.request(`/goals?page=${page}&size=20`, pageOf(goal), { signal }) });
    const items = query.data?.items.filter(item => !activeOnly || item.status === 'ACTIVE') ?? [];
    return <div><Select label={label} value={value} disabled={disabled || query.isPending || query.isError} onChange={event => onChange(event.target.value)}><option value="">{emptyLabel}</option>{value && !items.some(g => g.id === value) && <option value={value}>Current selection (not on this page)</option>}{items.map(g => <option key={g.id} value={g.id}>{g.title}{g.status === 'ARCHIVED' ? ' (archived)' : ''}</option>)}</Select>{query.isPending && <p role="status">Loading goals…</p>}{query.isError && <><Status error>Goal choices could not be loaded.</Status><Button variant="secondary" onClick={() => { void query.refetch(); }}>Retry goal choices</Button></>}{query.data && <Pagination data={query.data} page={page} onPage={setPage} label="Goal choices" disabled={disabled}/>}</div>;
}
export function DraftConflict({ stale, onKeep, onReplace }: {
    stale: boolean;
    onKeep: () => void;
    onReplace: () => void;
}) { return stale ? <aside className="conflict"><Status error>Another update was saved. Latest server details are shown above. Your draft is unchanged. Review the differences before continuing.</Status><div className="actions"><Button variant="secondary" onClick={onReplace}>Replace draft with latest values</Button><Button variant="secondary" onClick={onKeep}>Keep draft with latest version</Button></div></aside> : null; }
