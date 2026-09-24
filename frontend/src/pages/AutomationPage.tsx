import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useActiveClub } from '@/lib/club';
import { PRIORITY_LABEL, WEEKDAYS } from '@/lib/format';
import { useDepartments, useMembers } from '@/lib/queries';
import type { Frequency, Priority, RecurringTask, Template } from '@/lib/types';
import { Icon } from '@/components/Icon';
import { MemberPicker } from '@/components/MemberPicker';
import { PriorityMark } from '@/components/TaskBits';
import { useFeedback } from '@/components/feedback';
import { Badge, Button, Card, EmptyState, ErrorNote, Field, IconButton, Input, Loading, Modal, Notice, PageHeader, Select, Tabs, Textarea } from '@/components/ui';

export function AutomationPage() {
  const [tab, setTab] = useState<'templates' | 'recurring'>('templates');
  return (
    <>
      <PageHeader title="Templates and repeats" subtitle="Set up recurring work once so nobody has to remember to create it." />
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: 'templates', label: 'Event templates' },
          { value: 'recurring', label: 'Repeating tasks' },
        ]}
      />
      <div className="mt-5">{tab === 'templates' ? <Templates /> : <Recurring />}</div>
    </>
  );
}

// ------------------------------------------------------------------ templates

interface ItemDraft {
  key: number;
  title: string;
  priority: Priority;
  departmentId: string;
  daysBeforeEvent: number;
}

let draftKey = 1;
const blankItem = (): ItemDraft => ({ key: draftKey++, title: '', priority: 'MEDIUM', departmentId: '', daysBeforeEvent: 7 });

function Templates() {
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const [editing, setEditing] = useState<Template | 'new' | null>(null);

  const list = useQuery({
    queryKey: ['templates', clubId],
    queryFn: () => api<Template[]>('/api/templates', { query: { clubId } }),
    enabled: !!clubId,
  });

  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/templates/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['templates'] });
      toast('Template deleted');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(t: Template) {
    const ok = await confirm({ title: `Delete “${t.name}”?`, message: 'Tasks already created from it are not affected.', confirmLabel: 'Delete', danger: true });
    if (ok) remove.mutate(t.id);
  }

  if (list.isLoading) return <Loading />;
  if (list.isError) return <ErrorNote message={errorMessage(list.error)} onRetry={() => list.refetch()} />;

  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="max-w-xl text-sm text-ink-mute">
          A template is a checklist. Apply it to an event and each line becomes a task, due a set number of days before the event starts.
        </p>
        <Button variant="primary" icon="plus" onClick={() => setEditing('new')}>New template</Button>
      </div>

      {list.data && list.data.length > 0 ? (
        <div className="grid gap-4 lg:grid-cols-2">
          {list.data.map((t) => (
            <Card key={t.id} className="p-5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="font-display text-lg font-semibold">{t.name}</h3>
                  {t.description && <p className="mt-0.5 text-sm text-ink-mute">{t.description}</p>}
                </div>
                <div className="flex">
                  <IconButton icon="pencil" label={`Edit ${t.name}`} onClick={() => setEditing(t)} />
                  <IconButton icon="trash" label={`Delete ${t.name}`} onClick={() => onDelete(t)} className="hover:bg-rose-50 hover:text-rose-700" />
                </div>
              </div>
              <ol className="mt-4 divide-y divide-chalk-200 rounded-lg border border-chalk-200">
                {t.items.map((i) => (
                  <li key={i.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{i.title}</span>
                      <span className="text-xs text-ink-mute">{i.departmentName ?? 'Any department'}</span>
                    </span>
                    <span className="flex flex-none items-center gap-3 text-xs text-ink-mute">
                      <PriorityMark priority={i.priority} />
                      <span>{i.daysBeforeEvent === 0 ? 'On the day' : `${i.daysBeforeEvent} days before`}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <EmptyState icon="template" title="No templates yet">
            Create one for the tasks every event needs, like poster design, sponsor outreach and the registration form.
          </EmptyState>
        </Card>
      )}

      <TemplateModal target={editing} onClose={() => setEditing(null)} />
    </>
  );
}

function TemplateModal({ target, onClose }: { target: Template | 'new' | null; onClose: () => void }) {
  const { clubId } = useActiveClub();
  const { toast } = useFeedback();
  const qc = useQueryClient();
  const departments = useDepartments();
  const existing = target && target !== 'new' ? target : null;

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loadedFor, setLoadedFor] = useState<unknown>(null);

  if (target !== loadedFor) {
    setLoadedFor(target);
    setError(null);
    setName(existing?.name ?? '');
    setDescription(existing?.description ?? '');
    setItems(
      existing
        ? existing.items.map((i) => ({ key: draftKey++, title: i.title, priority: i.priority, departmentId: i.departmentId ?? '', daysBeforeEvent: i.daysBeforeEvent }))
        : [blankItem()],
    );
  }

  const update = (key: number, patch: Partial<ItemDraft>) => setItems((list) => list.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        clubId,
        items: items.map((i) => ({
          title: i.title.trim(),
          description: null,
          priority: i.priority,
          departmentId: i.departmentId || null,
          daysBeforeEvent: i.daysBeforeEvent,
        })),
      };
      return existing
        ? api<Template>(`/api/templates/${existing.id}`, { method: 'PUT', body })
        : api<Template>('/api/templates', { method: 'POST', body });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['templates'] });
      toast(existing ? 'Template saved' : 'Template created');
      onClose();
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : errorMessage(err)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (items.some((i) => !i.title.trim())) {
      setError('Every line needs a title.');
      return;
    }
    save.mutate();
  }

  return (
    <Modal
      open={target !== null}
      onClose={onClose}
      wide
      title={existing ? 'Edit template' : 'New template'}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} onClick={() => (document.getElementById('template-form') as HTMLFormElement | null)?.requestSubmit()}>
            Save template
          </Button>
        </>
      }
    >
      <form id="template-form" onSubmit={submit} className="space-y-4">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Name">
          <Input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} placeholder="Event campaign" autoFocus />
        </Field>
        <Field label="Description">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} className="min-h-[60px]" />
        </Field>

        <div>
          <p className="mb-2 text-sm font-semibold text-ink-soft">Tasks</p>
          <ul className="space-y-3">
            {items.map((item, idx) => (
              <li key={item.key} className="rounded-lg border border-chalk-200 bg-chalk-50 p-3">
                <div className="flex gap-2">
                  <Input value={item.title} onChange={(e) => update(item.key, { title: e.target.value })} placeholder={`Task ${idx + 1}`} aria-label={`Task ${idx + 1} title`} maxLength={200} />
                  <IconButton icon="x" label="Remove task" disabled={items.length === 1} onClick={() => setItems((l) => l.filter((i) => i.key !== item.key))} />
                </div>
                <div className="mt-2 grid gap-2 sm:grid-cols-3">
                  <Select aria-label="Priority" value={item.priority} onChange={(e) => update(item.key, { priority: e.target.value as Priority })}>
                    {(Object.keys(PRIORITY_LABEL) as Priority[]).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]} priority</option>)}
                  </Select>
                  <Select aria-label="Department" value={item.departmentId} onChange={(e) => update(item.key, { departmentId: e.target.value })}>
                    <option value="">Any department</option>
                    {departments.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </Select>
                  <label className="flex items-center gap-2 text-sm text-ink-soft">
                    <Input type="number" min={0} max={365} value={item.daysBeforeEvent} onChange={(e) => update(item.key, { daysBeforeEvent: Math.max(0, Math.min(365, Number(e.target.value) || 0)) })} className="w-20" aria-label="Days before the event" />
                    days before
                  </label>
                </div>
              </li>
            ))}
          </ul>
          <Button size="sm" icon="plus" className="mt-3" onClick={() => setItems((l) => [...l, blankItem()])}>Add task</Button>
        </div>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ recurring

function schedule(r: RecurringTask): string {
  const when = r.frequency === 'DAILY' ? 'Every day' : r.frequency === 'WEEKLY' ? `Every ${WEEKDAYS[(r.dayOfWeek ?? 1) - 1]}` : `On day ${r.dayOfMonth} of every month`;
  const [h, m] = r.dueTime.split(':').map(Number);
  const time = new Date(2000, 0, 1, h, m).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
  const due = r.dueInDays === 0 ? `due the same day at ${time}` : `due ${r.dueInDays} ${r.dueInDays === 1 ? 'day' : 'days'} later at ${time}`;
  return `${when}, ${due}`;
}

function Recurring() {
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const [editing, setEditing] = useState<RecurringTask | 'new' | null>(null);

  const list = useQuery({
    queryKey: ['recurring', clubId],
    queryFn: () => api<RecurringTask[]>('/api/recurring-tasks', { query: { clubId } }),
    enabled: !!clubId,
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['recurring'] });

  const toggle = useMutation({
    mutationFn: (r: RecurringTask) => api<RecurringTask>(`/api/recurring-tasks/${r.id}/${r.active ? 'pause' : 'resume'}`, { method: 'POST' }),
    onSuccess: (r) => { refresh(); toast(r.active ? 'Resumed' : 'Paused'); },
    onError: (err) => toast(errorMessage(err), 'error'),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/recurring-tasks/${id}`, { method: 'DELETE' }),
    onSuccess: () => { refresh(); toast('Repeating task deleted'); },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(r: RecurringTask) {
    const ok = await confirm({ title: `Delete “${r.title}”?`, message: 'No more tasks will be created. Tasks that already exist stay.', confirmLabel: 'Delete', danger: true });
    if (ok) remove.mutate(r.id);
  }

  if (list.isLoading) return <Loading />;
  if (list.isError) return <ErrorNote message={errorMessage(list.error)} onRetry={() => list.refetch()} />;

  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="max-w-xl text-sm text-ink-mute">A new task appears on the schedule you choose and is assigned automatically, for things like the weekly meeting report.</p>
        <Button variant="primary" icon="plus" onClick={() => setEditing('new')}>New repeating task</Button>
      </div>

      <Card>
        {list.data && list.data.length > 0 ? (
          <ul>
            {list.data.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-chalk-200 px-5 py-4 last:border-0">
                <div className="min-w-0 flex-1 basis-64">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold">{r.title}</p>
                    {!r.active && <Badge>Paused</Badge>}
                  </div>
                  <p className="mt-0.5 flex items-center gap-1.5 text-sm text-ink-mute"><Icon name="repeat" size={14} /> {schedule(r)}</p>
                  <p className="mt-0.5 text-sm text-ink-mute">
                    {r.assignees.length > 0 ? r.assignees.map((a) => a.name).join(', ') : 'Unassigned'}
                    {r.departmentName ? `, ${r.departmentName}` : ''}
                  </p>
                </div>
                <PriorityMark priority={r.priority} />
                <div className="flex gap-1">
                  <Button size="sm" loading={toggle.isPending && toggle.variables?.id === r.id} onClick={() => toggle.mutate(r)}>{r.active ? 'Pause' : 'Resume'}</Button>
                  <IconButton icon="pencil" label={`Edit ${r.title}`} onClick={() => setEditing(r)} />
                  <IconButton icon="trash" label={`Delete ${r.title}`} onClick={() => onDelete(r)} className="hover:bg-rose-50 hover:text-rose-700" />
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState icon="repeat" title="Nothing repeats yet">Create one for the routine work your club does every week or month.</EmptyState>
        )}
      </Card>

      <RecurringModal target={editing} onClose={() => setEditing(null)} onSaved={refresh} />
    </>
  );
}

function RecurringModal({ target, onClose, onSaved }: { target: RecurringTask | 'new' | null; onClose: () => void; onSaved: () => void }) {
  const { clubId } = useActiveClub();
  const { toast } = useFeedback();
  const departments = useDepartments();
  const members = useMembers(true);
  const existing = target && target !== 'new' ? target : null;

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [frequency, setFrequency] = useState<Frequency>('WEEKLY');
  const [dayOfWeek, setDayOfWeek] = useState(1);
  const [dayOfMonth, setDayOfMonth] = useState(1);
  const [dueInDays, setDueInDays] = useState(3);
  const [dueTime, setDueTime] = useState('18:00');
  const [departmentId, setDepartmentId] = useState('');
  const [assignees, setAssignees] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loadedFor, setLoadedFor] = useState<unknown>(null);

  if (target !== loadedFor) {
    setLoadedFor(target);
    setError(null);
    setTitle(existing?.title ?? '');
    setDescription(existing?.description ?? '');
    setPriority(existing?.priority ?? 'MEDIUM');
    setFrequency(existing?.frequency ?? 'WEEKLY');
    setDayOfWeek(existing?.dayOfWeek ?? 1);
    setDayOfMonth(existing?.dayOfMonth ?? 1);
    setDueInDays(existing?.dueInDays ?? 3);
    setDueTime(existing?.dueTime.slice(0, 5) ?? '18:00');
    setDepartmentId(existing?.departmentId ?? '');
    setAssignees(existing?.assignees.map((a) => a.id) ?? []);
  }

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title: title.trim(),
        description: description.trim() || null,
        priority,
        frequency,
        dayOfWeek: frequency === 'WEEKLY' ? dayOfWeek : null,
        dayOfMonth: frequency === 'MONTHLY' ? dayOfMonth : null,
        dueInDays,
        dueTime: dueTime.length === 5 ? `${dueTime}:00` : dueTime,
        departmentId: departmentId || null,
        assigneeIds: assignees,
        active: existing?.active ?? true,
        clubId,
      };
      return existing
        ? api<RecurringTask>(`/api/recurring-tasks/${existing.id}`, { method: 'PUT', body })
        : api<RecurringTask>('/api/recurring-tasks', { method: 'POST', body });
    },
    onSuccess: () => {
      toast(existing ? 'Saved' : 'Repeating task created');
      onSaved();
      onClose();
    },
    onError: (err) => setError(errorMessage(err)),
  });

  return (
    <Modal
      open={target !== null}
      onClose={onClose}
      wide
      title={existing ? 'Edit repeating task' : 'New repeating task'}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" disabled={!title.trim()} loading={save.isPending} onClick={() => save.mutate()}>Save</Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Title">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder="Weekly meeting report" autoFocus />
        </Field>
        <Field label="Details">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={5000} className="min-h-[60px]" />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Repeats">
            <Select value={frequency} onChange={(e) => setFrequency(e.target.value as Frequency)}>
              <option value="DAILY">Every day</option>
              <option value="WEEKLY">Every week</option>
              <option value="MONTHLY">Every month</option>
            </Select>
          </Field>
          {frequency === 'WEEKLY' && (
            <Field label="On">
              <Select value={dayOfWeek} onChange={(e) => setDayOfWeek(Number(e.target.value))}>
                {WEEKDAYS.map((d, i) => <option key={d} value={i + 1}>{d}</option>)}
              </Select>
            </Field>
          )}
          {frequency === 'MONTHLY' && (
            <Field label="On day" hint="Short months use their last day.">
              <Input type="number" min={1} max={31} value={dayOfMonth} onChange={(e) => setDayOfMonth(Math.max(1, Math.min(31, Number(e.target.value) || 1)))} />
            </Field>
          )}
          <Field label="Due after (days)">
            <Input type="number" min={0} max={60} value={dueInDays} onChange={(e) => setDueInDays(Math.max(0, Math.min(60, Number(e.target.value) || 0)))} />
          </Field>
          <Field label="Due at">
            <Input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} />
          </Field>
          <Field label="Priority">
            <Select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {(Object.keys(PRIORITY_LABEL) as Priority[]).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
            </Select>
          </Field>
          <Field label="Department">
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">None</option>
              {departments.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </Select>
          </Field>
        </div>
        <Field label="Assign to" group>
          <MemberPicker members={members.data ?? []} selected={assignees} onChange={setAssignees} loading={members.isLoading} />
        </Field>
      </div>
    </Modal>
  );
}
