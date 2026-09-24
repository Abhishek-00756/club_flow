import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { useDebounced } from '@/lib/hooks';
import { fmtDate, ROLE_LABEL } from '@/lib/format';
import { useDepartments } from '@/lib/queries';
import type { Role, User } from '@/lib/types';
import { Icon } from '@/components/Icon';
import { useFeedback } from '@/components/feedback';
import { Avatar, Badge, Button, Card, EmptyState, ErrorNote, Field, IconButton, Input, Loading, Modal, Notice, PageHeader, SearchInput, Select } from '@/components/ui';

const RANK: Record<Role, number> = { SUPERADMIN: 5, ADMIN: 4, SECRETARY: 3, JOINT_SECRETARY: 2, MEMBER: 1 };
const ASSIGNABLE: Role[] = ['ADMIN', 'SECRETARY', 'JOINT_SECRETARY', 'MEMBER'];

export function MembersPage() {
  const { user: me, can } = useAuth();
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const departments = useDepartments();

  const [search, setSearch] = useState('');
  const q = useDebounced(search, 300);
  const [role, setRole] = useState<Role | ''>('');
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState<'' | 'true' | 'false'>('');
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [resetting, setResetting] = useState<User | null>(null);

  const list = useQuery({
    queryKey: ['members-page', clubId, { q, role, departmentId, status }],
    queryFn: () => api<User[]>('/api/users', { query: { clubId, q, role, departmentId, active: status } }),
    enabled: !!clubId,
  });

  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['members-page'] });
      qc.invalidateQueries({ queryKey: ['members'] });
      toast('Member removed');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(u: User) {
    const ok = await confirm({
      title: `Remove ${u.name}?`,
      message: 'This deletes the account. If they have tasks or events on record you will be asked to deactivate them instead, which keeps the history.',
      confirmLabel: 'Remove',
      danger: true,
    });
    if (ok) remove.mutate(u.id);
  }

  const myRank = me ? RANK[me.role] : 0;
  const canManage = (u: User) => u.id !== me?.id && RANK[u.role] < myRank;

  return (
    <>
      <PageHeader
        title="Members"
        subtitle="Everyone in the club and what they can do."
        actions={can('USER_CREATE') ? <Button variant="primary" icon="plus" onClick={() => setEditing('new')}>Add member</Button> : undefined}
      />

      <Card className="mb-4 p-3 sm:p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <SearchInput value={search} onChange={setSearch} placeholder="Search by name or email" />
          <Select aria-label="Role" value={role} onChange={(e) => setRole(e.target.value as Role | '')}>
            <option value="">Any role</option>
            {ASSIGNABLE.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </Select>
          <Select aria-label="Department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
            <option value="">Any department</option>
            {departments.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </Select>
          <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}>
            <option value="">Active and deactivated</option>
            <option value="true">Active only</option>
            <option value="false">Deactivated only</option>
          </Select>
        </div>
      </Card>

      <Card>
        {list.isLoading ? (
          <Loading />
        ) : list.isError ? (
          <div className="p-4"><ErrorNote message={errorMessage(list.error)} onRetry={() => list.refetch()} /></div>
        ) : list.data && list.data.length > 0 ? (
          <ul>
            {list.data.map((u) => (
              <li key={u.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-chalk-200 px-4 py-3.5 last:border-0 sm:px-5">
                <Avatar name={u.name} size={36} />
                <div className="min-w-0 flex-1 basis-48">
                  <p className="truncate font-semibold">{u.name}{u.id === me?.id && <span className="ml-1.5 font-normal text-ink-mute">(you)</span>}</p>
                  <p className="truncate text-sm text-ink-mute">{u.email}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={u.role === 'MEMBER' ? 'neutral' : 'sky'}>{ROLE_LABEL[u.role]}</Badge>
                  {!u.active && <Badge tone="rose">Deactivated</Badge>}
                </div>
                <p className="w-32 text-sm text-ink-soft">{u.departmentName ?? 'No department'}</p>
                <p className="hidden w-28 text-sm text-ink-mute md:block">{u.lastLoginAt ? `Seen ${fmtDate(u.lastLoginAt)}` : 'Never signed in'}</p>
                <div className="flex gap-1">
                  {can('USER_UPDATE') && (u.id === me?.id || canManage(u)) && <IconButton icon="pencil" label={`Edit ${u.name}`} onClick={() => setEditing(u)} />}
                  {can('USER_UPDATE') && canManage(u) && <IconButton icon="refresh" label={`Reset password for ${u.name}`} onClick={() => setResetting(u)} />}
                  {can('USER_DELETE') && canManage(u) && <IconButton icon="trash" label={`Remove ${u.name}`} onClick={() => onDelete(u)} className="hover:bg-rose-50 hover:text-rose-700" />}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState icon="users" title="No one matches">Try a different filter, or add people with the button above.</EmptyState>
        )}
      </Card>

      <MemberFormModal
        target={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          qc.invalidateQueries({ queryKey: ['members-page'] });
          qc.invalidateQueries({ queryKey: ['members'] });
          qc.invalidateQueries({ queryKey: ['clubs'] });
        }}
      />
      <ResetPasswordModal user={resetting} onClose={() => setResetting(null)} />
    </>
  );
}

function MemberFormModal({ target, onClose, onSaved }: { target: User | 'new' | null; onClose: () => void; onSaved: () => void }) {
  const { user: me, setUser } = useAuth();
  const { clubId } = useActiveClub();
  const { toast } = useFeedback();
  const departments = useDepartments();
  const isNew = target === 'new';
  const existing = target && target !== 'new' ? target : null;
  const isSelf = !!existing && existing.id === me?.id;

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [departmentId, setDepartmentId] = useState('');
  const [active, setActive] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loadedFor, setLoadedFor] = useState<unknown>(null);

  // Load the form once per target (state initialisers would not re-run when the same modal is reused).
  if (target !== loadedFor) {
    setLoadedFor(target);
    setError(null);
    setFieldErrors({});
    setPassword('');
    setName(existing?.name ?? '');
    setEmail(existing?.email ?? '');
    setRole(existing?.role ?? 'MEMBER');
    setDepartmentId(existing?.departmentId ?? '');
    setActive(existing?.active ?? true);
  }

  const myRank = me ? RANK[me.role] : 0;
  const roleOptions = ASSIGNABLE.filter((r) => RANK[r] < myRank || (existing && existing.role === r));

  const save = useMutation({
    mutationFn: () =>
      existing
        ? api<User>(`/api/users/${existing.id}`, { method: 'PUT', body: { name: name.trim(), role, departmentId: departmentId || null, active } })
        : api<User>('/api/users', { method: 'POST', body: { name: name.trim(), email: email.trim(), password, role, departmentId: departmentId || null, clubId } }),
    onSuccess: (saved) => {
      if (isSelf) setUser({ ...saved, permissions: me?.permissions ?? saved.permissions });
      toast(existing ? 'Member updated' : 'Member added');
      onSaved();
      onClose();
    },
    onError: (err) => {
      setError(errorMessage(err));
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    save.mutate();
  }

  return (
    <Modal
      open={target !== null}
      onClose={onClose}
      title={isNew ? 'Add a member' : 'Edit member'}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} onClick={() => (document.getElementById('member-form') as HTMLFormElement | null)?.requestSubmit()}>
            {isNew ? 'Add member' : 'Save changes'}
          </Button>
        </>
      }
    >
      <form id="member-form" onSubmit={submit} className="space-y-4">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Full name" error={fieldErrors.name}>
          <Input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} autoFocus />
        </Field>
        <Field label="Email" error={fieldErrors.email}>
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required disabled={!isNew} />
        </Field>
        {isNew && (
          <Field label="Temporary password" hint="At least 8 characters. Ask them to change it after signing in." error={fieldErrors.password}>
            <Input type="text" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} autoComplete="off" />
          </Field>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Role" hint={isSelf ? 'You cannot change your own role.' : undefined}>
            <Select value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={isSelf}>
              {roleOptions.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
            </Select>
          </Field>
          <Field label="Department">
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">None</option>
              {departments.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </Select>
          </Field>
        </div>
        {!isNew && (
          <label className="flex items-center gap-2.5 text-sm">
            <input type="checkbox" checked={active} disabled={isSelf} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-marker-600" />
            <span>Account is active {isSelf && <span className="text-ink-mute">(you cannot deactivate yourself)</span>}</span>
          </label>
        )}
      </form>
    </Modal>
  );
}

function ResetPasswordModal({ user, onClose }: { user: User | null; onClose: () => void }) {
  const { toast } = useFeedback();
  const [password, setPassword] = useState('');
  const [seen, setSeen] = useState<User | null>(null);
  if (user !== seen) {
    setSeen(user);
    setPassword('');
  }

  const reset = useMutation({
    mutationFn: () => api(`/api/users/${user!.id}/reset-password`, { method: 'POST', body: { newPassword: password } }),
    onSuccess: () => {
      toast('Password reset. Share the new one with them privately.');
      onClose();
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  return (
    <Modal
      open={user !== null}
      onClose={onClose}
      title={`Reset password for ${user?.name ?? ''}`}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" disabled={password.length < 8} loading={reset.isPending} onClick={() => reset.mutate()}>Reset password</Button>
        </>
      }
    >
      <p className="mb-4 flex gap-2 text-sm text-ink-soft"><Icon name="shield" size={16} className="mt-0.5 flex-none" />Their current sessions end within a few minutes, and they need the new password to sign in again.</p>
      <Field label="New password" hint="At least 8 characters.">
        <Input type="text" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="off" autoFocus />
      </Field>
    </Modal>
  );
}
