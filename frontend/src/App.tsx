import type { ReactNode } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './lib/auth';
import { ClubProvider } from './lib/club';
import { Layout } from './components/Layout';
import { EmptyState, Loading } from './components/ui';
import { AuthPage } from './pages/AuthPage';
import { Dashboard } from './pages/Dashboard';
import { TasksPage } from './pages/TasksPage';
import { TaskDetailPage } from './pages/TaskDetailPage';
import { EventsPage } from './pages/EventsPage';
import { EventDetailPage } from './pages/EventDetailPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { MembersPage } from './pages/MembersPage';
import { AutomationPage } from './pages/AutomationPage';
import { ClubSettingsPage } from './pages/ClubSettingsPage';
import { RolesPage } from './pages/RolesPage';
import { AuditPage } from './pages/AuditPage';
import { SystemPage } from './pages/SystemPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { SettingsPage } from './pages/SettingsPage';

function Protected() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading label="Signing you in" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return (
    <ClubProvider>
      <Layout />
    </ClubProvider>
  );
}

/** Hides a page from people whose role does not include the permission. The server enforces this too. */
function Guard({ any, children }: { any: string[]; children: ReactNode }) {
  const { canAny } = useAuth();
  if (!canAny(...any)) {
    return (
      <EmptyState icon="shield" title="This page is not available for your role">
        Ask a club admin if you think you should have access.
      </EmptyState>
    );
  }
  return <>{children}</>;
}

function NotFound() {
  return (
    <EmptyState icon="search" title="We could not find that page">
      <Link to="/" className="font-semibold text-marker-700 hover:underline">
        Back to the dashboard
      </Link>
    </EmptyState>
  );
}

export function App() {
  const { user, loading } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={!loading && user ? <Navigate to="/" replace /> : <AuthPage mode="login" />} />
      <Route path="/register" element={!loading && user ? <Navigate to="/" replace /> : <AuthPage mode="register" />} />

      <Route element={<Protected />}>
        <Route index element={<Dashboard />} />
        <Route
          path="tasks"
          element={
            <Guard any={['TASK_VIEW_ALL', 'TASK_VIEW_ASSIGNED']}>
              <TasksPage />
            </Guard>
          }
        />
        <Route
          path="tasks/:id"
          element={
            <Guard any={['TASK_VIEW_ALL', 'TASK_VIEW_ASSIGNED']}>
              <TaskDetailPage />
            </Guard>
          }
        />
        <Route
          path="events"
          element={
            <Guard any={['EVENT_VIEW']}>
              <EventsPage />
            </Guard>
          }
        />
        <Route
          path="events/:id"
          element={
            <Guard any={['EVENT_VIEW']}>
              <EventDetailPage />
            </Guard>
          }
        />
        <Route path="announcements" element={<AnnouncementsPage />} />
        <Route
          path="members"
          element={
            <Guard any={['USER_VIEW']}>
              <MembersPage />
            </Guard>
          }
        />
        <Route
          path="automation"
          element={
            <Guard any={['TEMPLATE_MANAGE']}>
              <AutomationPage />
            </Guard>
          }
        />
        <Route
          path="club"
          element={
            <Guard any={['CLUB_UPDATE', 'DEPARTMENT_MANAGE', 'CLUB_MANAGE']}>
              <ClubSettingsPage />
            </Guard>
          }
        />
        <Route
          path="roles"
          element={
            <Guard any={['SYSTEM_CONFIG']}>
              <RolesPage />
            </Guard>
          }
        />
        <Route
          path="audit"
          element={
            <Guard any={['AUDIT_VIEW']}>
              <AuditPage />
            </Guard>
          }
        />
        <Route
          path="system"
          element={
            <Guard any={['SYSTEM_CONFIG']}>
              <SystemPage />
            </Guard>
          }
        />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
