import { useEffect } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, api, sessionEvents } from '../shared/api/client';
import { useSession } from '../features/auth/session';
import { EmptyState, Loading, Status, Button } from '../shared/ui/components';
const sections = ['Dashboard', 'Goals', 'Tasks', 'Habits', 'Analytics', 'Settings'];
function Heading({ title, eyebrow = 'Belsen foundation' }: { title: string; eyebrow?: string }) { useEffect(() => { document.getElementById('page-title')?.focus(); }, [title]); return <header className="page-heading"><p className="eyebrow">{eyebrow}</p><h1 id="page-title" tabIndex={-1}>{title}</h1></header>; }
function Placeholder({ title }: { title: string }) { return <><Heading title={title}/><EmptyState title="A considered start">This frontend workflow is not implemented yet. More tools for your daily progress are planned for a later stage.</EmptyState></>; }
function Gate() {
  const session = useSession();
  if (session.isPending) return <main id="main" tabIndex={-1} className="page"><Loading/></main>;
  if (session.error instanceof ApiError && session.error.problem.status === 401) return <Navigate to="/auth" replace/>;
  if (session.isError) return <main id="main" tabIndex={-1} className="page"><Status error>We could not verify your session. No private content was loaded.</Status><Button onClick={() => { void session.refetch(); }}>Try again</Button></main>;
  return <Outlet/>;
}
function Layout() { return <div className="app-shell"><aside className="sidebar"><Link to="/" className="brand" aria-label="Belsen home">belsen<span>.</span></Link><p className="nav-label">YOUR WORKSPACE</p><nav aria-label="Workspace">{sections.map(s => <NavLink key={s} to={`/app/${s.toLowerCase()}`}>{s}</NavLink>)}</nav><p className="sidebar-note">Plan better.<br/>Stay consistent. Grow.</p></aside><main id="main" tabIndex={-1} className="workspace"><Outlet/></main></div>; }
function Welcome() { return <main id="main" tabIndex={-1} className="welcome"><header><Link to="/" className="brand">belsen<span>.</span></Link><span className="badge">Frontend foundation</span></header><section className="hero"><p className="eyebrow">A little direction. Every day.</p><h1 tabIndex={-1}>Make room<br/>for progress<span>.</span></h1><p className="intro">A clearer place for your goals, daily actions and routines. Built around progress you can understand.</p><Link className="button button-primary" to="/app/dashboard">Check my workspace <span aria-hidden="true">↗</span></Link><p className="release-note">The application shell is ready. Frontend product workflows are not implemented yet.</p></section><section className="principles" aria-label="Product direction"><article><span>01</span><h2>Choose a direction</h2><p>Give your goals a measurable shape.</p></article><article><span>02</span><h2>Build a rhythm</h2><p>Make space for actions and routines.</p></article><article><span>03</span><h2>See what matters</h2><p>Understand verified progress, without invented scores.</p></article></section><footer>Thoughtful routines. Honest progress. <span>AI Coach is planned.</span></footer></main>; }
function Auth() { return <main id="main" tabIndex={-1} className="page auth-page"><Link className="brand" to="/">belsen<span>.</span></Link><Heading title="Your next chapter" eyebrow="Authentication"/><EmptyState title="Sign-in interface coming next">Authentication forms and Google browser integration are not implemented in this stage. Protected pages require a real existing backend session.</EmptyState><Link className="text-link" to="/">Back to home</Link></main>; }
function NotFound() { return <main id="main" tabIndex={-1} className="page"><Heading title="This page is out of the plan" eyebrow="404"/><p>The requested page could not be found.</p><Link className="button button-primary" to="/">Return home</Link></main>; }
export function App() {
  const location = useLocation(); const client = useQueryClient();
  useEffect(() => {
    const part = location.pathname.split('/').filter(Boolean).at(-1) ?? 'Home';
    document.title = `${part.charAt(0).toUpperCase() + part.slice(1)} · Belsen`;
  }, [location.pathname]);
  useEffect(() => {
    const expire = () => { void client.cancelQueries({ predicate: query => query.queryKey[0] !== 'session' }); client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' }); api.clear(); };
    sessionEvents.addEventListener('expired', expire); return () => sessionEvents.removeEventListener('expired', expire);
  }, [client]);
  return <><a className="skip-link" href="#main">Skip to content</a><Routes><Route path="/" element={<Welcome/>}/><Route path="/auth" element={<Auth/>}/><Route path="/app" element={<Gate/>}><Route element={<Layout/>}><Route index element={<Navigate to="dashboard" replace/>}/>{sections.map(title => <Route key={title} path={title.toLowerCase()} element={<Placeholder title={title}/>}/>)}</Route></Route><Route path="*" element={<NotFound/>}/></Routes></>;
}
