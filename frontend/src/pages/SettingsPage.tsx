import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtDateTime, ROLE_LABEL } from '@/lib/format';
import type { Session, User } from '@/lib/types';
import { useFeedback } from '@/components/feedback';
import { Badge, Button, Card, Checkbox, Field, Input, Loading, Notice, PageHeader, Section } from '@/components/ui';

export function SettingsPage() {
  return (
    <>
      <PageHeader title="Profile and security" />
      <div className="mx-auto max-w-2xl space-y-6">
        <Profile />
        <Password />
        <Sessions />
      </div>
    </>
  );
}

function Profile() {
  const { user, setUser } = useAuth();
  const { toast } = useFeedback();
  const [name, setName] = useState(user?.name ?? '');
  const [emails, setEmails] = useState(user?.emailNotifications ?? true);

  const save = useMutation({
    mutationFn: () => api<User>('/api/users/me', { method: 'PATCH', body: { name: name.trim(), emailNotifications: emails } }),
    onSuccess: (saved) => {
      setUser({ ...saved, permissions: user?.permissions ?? saved.permissions });
      toast('Profile saved');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  if (!user) return null;
  const dirty = name.trim() !== user.name || emails !== user.emailNotifications;

  return (
    <Section title="Your profile">
      <form
        className="space-y-4 p-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="sky">{ROLE_LABEL[user.role]}</Badge>
          {user.clubName && <span className="text-sm text-ink-mute">{user.clubName}</span>}
          {user.departmentName && <span className="text-sm text-ink-mute">, {user.departmentName}</span>}
        </div>
        <Field label="Full name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        </Field>
        <Field label="Email" hint="Your email is your sign-in name. Ask an admin if it needs to change.">
          <Input value={user.email} disabled />
        </Field>
        <Checkbox checked={emails} onChange={setEmails} label="Email me about assignments, reviews and reminders" />
        <p className="-mt-2 pl-6 text-xs text-ink-mute">You still get in-app notifications either way.</p>
        <div className="flex justify-end">
          <Button type="submit" variant="primary" disabled={!dirty || !name.trim()} loading={save.isPending}>Save</Button>
        </div>
      </form>
    </Section>
  );
}

function Password() {
  const { logout } = useAuth();
  const { toast } = useFeedback();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const change = useMutation({
    mutationFn: () => api('/api/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } }),
    onSuccess: async () => {
      // The server ends every session when the password changes, this one included.
      await logout();
      navigate('/login', { replace: true });
      toast('Password changed. Please sign in again.');
    },
    onError: (err) => {
      setError(errorMessage(err));
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    if (next !== confirm) {
      setError('The new passwords do not match.');
      return;
    }
    change.mutate();
  }

  return (
    <Section title="Change password">
      <form onSubmit={submit} className="space-y-4 p-5">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Current password" error={fieldErrors.currentPassword}>
          <Input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="New password" hint="At least 8 characters." error={fieldErrors.newPassword}>
            <Input type="password" value={next} onChange={(e) => setNext(e.target.value)} required minLength={8} maxLength={72} autoComplete="new-password" />
          </Field>
          <Field label="Repeat new password">
            <Input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required minLength={8} autoComplete="new-password" />
          </Field>
        </div>
        <p className="text-sm text-ink-mute">Changing your password signs you out on every device.</p>
        <div className="flex justify-end">
          <Button type="submit" variant="primary" loading={change.isPending}>Change password</Button>
        </div>
      </form>
    </Section>
  );
}

function Sessions() {
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['sessions'], queryFn: () => api<Session[]>('/api/auth/sessions') });

  const revoke = useMutation({
    mutationFn: (id: string) => api(`/api/auth/sessions/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] });
      toast('Device signed out');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onRevoke(s: Session) {
    const ok = await confirm({
      title: 'Sign this device out?',
      message: 'If this is the device you are using right now, you will be asked to sign in again shortly.',
      confirmLabel: 'Sign out device',
      danger: true,
    });
    if (ok) revoke.mutate(s.id);
  }

  return (
    <Section title="Where you are signed in">
      {q.isLoading ? (
        <Loading />
      ) : (
        <ul>
          {q.data?.map((s) => (
            <li key={s.id} className="flex items-center justify-between gap-3 border-b border-chalk-200 px-5 py-3.5 last:border-0">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{describeAgent(s.userAgent)}</p>
                <p className="text-xs text-ink-mute">
                  Signed in {fmtDateTime(s.createdAt)}
                  {s.ipAddress ? `, ${s.ipAddress}` : ''}
                </p>
              </div>
              <Button size="sm" onClick={() => onRevoke(s)}>Sign out</Button>
            </li>
          ))}
          {q.data?.length === 0 && <li className="px-5 py-5 text-sm text-ink-mute">No other active sessions.</li>}
        </ul>
      )}
      <Card className="rounded-t-none border-0 border-t border-chalk-200 shadow-none">
        <p className="px-5 py-3 text-xs text-ink-mute">Sessions expire on their own after two weeks without being renewed.</p>
      </Card>
    </Section>
  );
}

/** A short, human label from a raw User-Agent string. */
function describeAgent(ua: string | null): string {
  if (!ua) return 'Unknown device';
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : '';
  return os ? `${browser} on ${os}` : browser;
}
