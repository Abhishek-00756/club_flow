import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { useDepartments } from '@/lib/queries';
import type { Club, Department } from '@/lib/types';
import { useFeedback } from '@/components/feedback';
import { Badge, Button, Card, EmptyState, Field, IconButton, Input, Loading, Modal, PageHeader, Section, Textarea } from '@/components/ui';

export function ClubSettingsPage() {
  const { can } = useAuth();
  const { club, clubs, isSuper, loading } = useActiveClub();
  const [creating, setCreating] = useState(false);

  if (loading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Club settings"
        subtitle={club ? club.name : 'No club selected'}
        actions={can('CLUB_MANAGE') ? <Button variant="primary" icon="plus" onClick={() => setCreating(true)}>New club</Button> : undefined}
      />

      {!club ? (
        <Card>
          <EmptyState icon="building" title="No club yet">
            {can('CLUB_MANAGE') ? 'Create the first club to get started.' : 'Your account is not attached to a club.'}
          </EmptyState>
        </Card>
      ) : (
        <div className="space-y-6">
          {can('CLUB_UPDATE') && <DetailsCard club={club} />}
          {can('CLUB_UPDATE') && <JoinCodeCard club={club} />}
          {can('DEPARTMENT_MANAGE') && <Departments />}
          {isSuper && clubs.length > 1 && <AllClubs clubs={clubs} />}
        </div>
      )}

      <NewClubModal open={creating} onClose={() => setCreating(false)} />
    </>
  );
}

function DetailsCard({ club }: { club: Club }) {
  const { toast } = useFeedback();
  const qc = useQueryClient();
  const [name, setName] = useState(club.name);
  const [description, setDescription] = useState(club.description ?? '');
  const [seen, setSeen] = useState(club.id);
  if (club.id !== seen) {
    setSeen(club.id);
    setName(club.name);
    setDescription(club.description ?? '');
  }

  const save = useMutation({
    mutationFn: () => api<Club>(`/api/clubs/${club.id}`, { method: 'PUT', body: { name: name.trim(), description: description.trim() || null, active: club.active } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['clubs'] });
      toast('Club details saved');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const dirty = name.trim() !== club.name || description.trim() !== (club.description ?? '');

  return (
    <Section title="About the club">
      <form
        className="space-y-4 p-5"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <Field label="Club name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        </Field>
        <Field label="Description" hint="Shown to people when they join.">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} />
        </Field>
        <div className="flex justify-end">
          <Button type="submit" variant="primary" disabled={!dirty || !name.trim()} loading={save.isPending}>Save changes</Button>
        </div>
      </form>
    </Section>
  );
}

function JoinCodeCard({ club }: { club: Club }) {
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();

  const regenerate = useMutation({
    mutationFn: () => api<Club>(`/api/clubs/${club.id}/join-code`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['clubs'] });
      toast('New join code generated');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onRegenerate() {
    const ok = await confirm({
      title: 'Generate a new join code?',
      message: 'The old code stops working straight away. People who already have accounts are not affected.',
      confirmLabel: 'Generate new code',
    });
    if (ok) regenerate.mutate();
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(club.joinCode);
      toast('Code copied');
    } catch {
      toast('Could not copy. Select the code and copy it manually.', 'error');
    }
  }

  return (
    <Section title="Join code">
      <div className="flex flex-wrap items-center justify-between gap-4 p-5">
        <div>
          <p className="font-display text-3xl font-bold tracking-widest tabular text-ink">{club.joinCode}</p>
          <p className="mt-1 text-sm text-ink-mute">New members enter this on the sign-up page to join {club.name}.</p>
        </div>
        <div className="flex gap-2">
          <Button icon="copy" onClick={copy}>Copy</Button>
          <Button icon="refresh" loading={regenerate.isPending} onClick={onRegenerate}>New code</Button>
        </div>
      </div>
    </Section>
  );
}

function Departments() {
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const departments = useDepartments();
  const [name, setName] = useState('');
  const [renaming, setRenaming] = useState<Department | null>(null);
  const [newName, setNewName] = useState('');

  const refresh = () => qc.invalidateQueries({ queryKey: ['departments'] });

  const create = useMutation({
    mutationFn: () => api<Department>('/api/departments', { method: 'POST', body: { name: name.trim(), clubId } }),
    onSuccess: () => {
      refresh();
      setName('');
      toast('Department added');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });
  const rename = useMutation({
    mutationFn: () => api<Department>(`/api/departments/${renaming!.id}`, { method: 'PUT', body: { name: newName.trim(), clubId } }),
    onSuccess: () => {
      refresh();
      setRenaming(null);
      toast('Department renamed');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/departments/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      refresh();
      toast('Department deleted');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(d: Department) {
    const ok = await confirm({
      title: `Delete ${d.name}?`,
      message: 'Members and tasks in this department keep existing but are no longer in a department.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (ok) remove.mutate(d.id);
  }

  return (
    <Section title="Departments">
      {departments.isLoading ? (
        <Loading />
      ) : (
        <ul>
          {departments.data?.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-3 border-b border-chalk-200 px-5 py-3">
              <span className="font-semibold">{d.name}</span>
              <span className="flex">
                <IconButton icon="pencil" label={`Rename ${d.name}`} onClick={() => { setRenaming(d); setNewName(d.name); }} />
                <IconButton icon="trash" label={`Delete ${d.name}`} onClick={() => onDelete(d)} className="hover:bg-rose-50 hover:text-rose-700" />
              </span>
            </li>
          ))}
          {departments.data?.length === 0 && <li className="px-5 py-6 text-sm text-ink-mute">No departments yet. Add ones like Design, Content, Outreach or Logistics.</li>}
        </ul>
      )}
      <form
        className="flex gap-2 p-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) create.mutate();
        }}
      >
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New department name" maxLength={80} aria-label="New department name" />
        <Button type="submit" variant="primary" icon="plus" disabled={!name.trim()} loading={create.isPending}>Add</Button>
      </form>

      <Modal
        open={renaming !== null}
        onClose={() => setRenaming(null)}
        title="Rename department"
        footer={
          <>
            <Button onClick={() => setRenaming(null)}>Cancel</Button>
            <Button variant="primary" disabled={!newName.trim()} loading={rename.isPending} onClick={() => rename.mutate()}>Save</Button>
          </>
        }
      >
        <Field label="Name">
          <Input value={newName} onChange={(e) => setNewName(e.target.value)} maxLength={80} autoFocus />
        </Field>
      </Modal>
    </Section>
  );
}

function AllClubs({ clubs }: { clubs: Club[] }) {
  const { setClubId, clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();

  const setActive = useMutation({
    mutationFn: (c: Club) => api<Club>(`/api/clubs/${c.id}`, { method: 'PUT', body: { name: c.name, description: c.description, active: !c.active } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['clubs'] }),
    onError: (err) => toast(errorMessage(err), 'error'),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/clubs/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['clubs'] });
      toast('Club deleted');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(c: Club) {
    const ok = await confirm({
      title: `Delete ${c.name}?`,
      message: 'This is only possible for a club with no members and no records. To retire a club that has history, deactivate it instead.',
      confirmLabel: 'Delete club',
      danger: true,
    });
    if (ok) remove.mutate(c.id);
  }

  return (
    <Section title="All clubs">
      <ul>
        {clubs.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-chalk-200 px-5 py-3.5 last:border-0">
            <div>
              <p className="flex items-center gap-2 font-semibold">
                {c.name}
                {c.id === clubId && <Badge tone="green">Selected</Badge>}
                {!c.active && <Badge tone="rose">Inactive</Badge>}
              </p>
              <p className="text-sm text-ink-mute">{c.memberCount} {c.memberCount === 1 ? 'member' : 'members'}</p>
            </div>
            <div className="flex gap-2">
              {c.id !== clubId && <Button size="sm" onClick={() => setClubId(c.id)}>Switch to</Button>}
              <Button size="sm" loading={setActive.isPending && setActive.variables?.id === c.id} onClick={() => setActive.mutate(c)}>{c.active ? 'Deactivate' : 'Activate'}</Button>
              <IconButton icon="trash" label={`Delete ${c.name}`} onClick={() => onDelete(c)} className="hover:bg-rose-50 hover:text-rose-700" />
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function NewClubModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { toast } = useFeedback();
  const { setClubId } = useActiveClub();
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const create = useMutation({
    mutationFn: () => api<Club>('/api/clubs', { method: 'POST', body: { name: name.trim(), description: description.trim() || null } }),
    onSuccess: async (club) => {
      await qc.invalidateQueries({ queryKey: ['clubs'] });
      setClubId(club.id);
      setName('');
      setDescription('');
      toast(`${club.name} created`);
      onClose();
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="New club"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" disabled={!name.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create club</Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Club name">
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus />
        </Field>
        <Field label="Description">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} />
        </Field>
        <p className="text-sm text-ink-mute">A join code is generated automatically. Add an admin from the Members page once the club exists.</p>
      </div>
    </Modal>
  );
}
