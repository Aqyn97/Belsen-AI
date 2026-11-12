import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
export function Button({ variant = 'primary', type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary'|'secondary' }) { return <button {...props} type={type} className={`button button-${variant}`} />; }
export function Field({ label, error, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const generated = useId(); const id = props.id ?? generated;
  return <div className="field"><label htmlFor={id}>{label}</label><input {...props} id={id} aria-invalid={error ? true : undefined} aria-describedby={[props['aria-describedby'], error ? `${id}-error` : undefined].filter(Boolean).join(' ') || undefined}/>{error && <span id={`${id}-error`} className="field-error">{error}</span>}</div>;
}
export function Status({ children, error = false }: { children: ReactNode; error?: boolean }) { return <div className={`status ${error ? 'error' : ''}`} role={error ? 'alert' : 'status'}>{children}</div>; }
export function Loading() { return <Status>Checking your session…</Status>; }
export function EmptyState({ title, children }: { title: string; children: ReactNode }) { return <section className="empty"><span className="empty-mark" aria-hidden="true">↗</span><h2>{title}</h2><p>{children}</p></section>; }
