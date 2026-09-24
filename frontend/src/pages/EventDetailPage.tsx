import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { fmtDateTime, fmtTime } from '@/lib/format';
import { useMembers } from '@/lib/queries';
import type { EventDetail, PageResponse, Participation, Task, Template } from '@/lib/types';
import { EventFormModal } from '@/components/EventFormModal';
import { Icon } from '@/components/Icon';
import { TaskFormModal } from '@/components/TaskFormModal';
import { TaskRow } from '@/components/TaskRow';
import { useFeedback } from '@/components/feedback';
import { Avatar, Badge, Button, Card, Checkbox, EmptyState, Field, IconButton, Loading, Modal, Section, Select } from '@/components/ui';

export function EventDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { can } = useAuth();
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();

  const [editing, setEditing] = useState(false);
  const [addingTask, setAddingTask] = useState(false);
  const [applying, setApplying] = useState(false);
  const [templateId, setTemplateId] = useState('');
  const [byDepartment, setByDepartment] = useState(false);
  const [addUserId, setAddUserId] = useState('');
  const [addRole, setAddRole] = useState<Participation>('VOLUNTEER');

  const canManage = can('EVENT_UPDATE');
  const members = useMembers(true);

  const detail = useQuery({ queryKey: ['event', id], queryFn: () => api<EventDetail>(`/api/events/${id}`) });
  const tasks = useQuery({
    queryKey: ['tasks', clubId, 'event', id],
    queryFn: () => api<PageResponse<Task>>('/api/tasks', { query: { clubId, eventId: id, size: 50 } }),
    enabled: !!clubId && (can('TASK_VIEW_ALL') || can('TASK_VIEW_ASSIGNED')),
  });
  const templates = useQuery({
    queryKey: ['templates', clubId],
    queryFn: () => api<Template[]>('/api/templates', { query: { clubId } }),
    enabled: applying && !!clubId,
  });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['event', id] });
    qc.invalidateQueries({ queryKey: ['events'] });
  };
  const onError = (err: unknown) => toast(errorMessage(err), 'error');

  const join = useMutation({
    mutationFn: (participation: Participation) => api(`/api/events/${id}/join`, { method: 'POST', body: { participation } }),
    onSuccess: () => { refresh(); toast('You are signed up'); },
    onError,
  });
  const leave = useMutation({
    mutationFn: () => api(`/api/events/${id}/join`, { method: 'DELETE' }),
    onSuccess: () => { refresh(); toast('You have left this event'); },
    onError,
  });
  const checkIn = useMutation({
    mutationFn: () => api(`/api/events/${id}/check-in`, { method: 'POST' }),
    onSuccess: () => { refresh(); toast('Checked in. Enjoy the event.'); },
    onError,
  });
  const addAttendee = useMutation({
    mutationFn: () => api(`/api/events/${id}/attendees`, { method: 'POST', body: { userId: addUserId, participation: addRole } }),
    onSuccess: () => { refresh(); setAddUserId(''); toast('Added to the event'); },
    onError,
  });
  const setAttended = useMutation({
    mutationFn: (v: { userId: string; attended: boolean }) =>
      api(`/api/events/${id}/attendees/${v.userId}/attendance`, { method: 'PUT', body: { attended: v.attended } }),
    onSuccess: refresh,
    onError,
  });
  const removeAttendee = useMutation({
    mutationFn: (userId: string) => api(`/api/events/${id}/attendees/${userId}`, { method: 'DELETE' }),
    onSuccess: refresh,
    onError,
  });
  const remove = useMutation({
    mutationFn: () => api(`/api/events/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['events'] });
      toast('Event deleted');
      navigate('/events', { replace: true });
    },
    onError,
  });
  const apply = useMutation({
    mutationFn: () => api<{ created: number }>(`/api/templates/${templateId}/apply`, { method: 'POST', body: { eventId: id, assignToDepartments: byDepartment } }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['tasks'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      refresh();
      setApplying(false);
      toast(`${res.created} tasks created`);
    },
    onError,
  });

  if (detail.isLoading) return <Loading />;
  if (detail.isError || !detail.data) {
    return (
      <>
        <BackLink />
        <EmptyState icon="calendar" title="Event not found">{errorMessage(detail.error)}</EmptyState>
      </>
    );
  }

  const { event, attendees } = detail.data;
  const now = Date.now();
  const start = new Date(event.startsAt).getTime();
  const end = new Date(event.endsAt).getTime();
  const finished = end < now;
  const checkInOpen = now >= start - 3600_000 && now <= end;
  const signedUp = event.myParticipation !== null;
  const addable = (members.data ?? []).filter((m) => !attendees.some((a) => a.userId === m.id));

  async function onDelete() {
    const ok = await confirm({
      title: 'Delete this event?',
      message: 'Sign-ups are removed. Tasks linked to the event stay, but are no longer linked.',
      confirmLabel: 'Delete event',
      danger: true,
    });
    if (ok) remove.mutate();
  }

  return (
    <>
      <BackLink />

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="mb-2 flex items-center gap-2">
            {finished ? <Badge>Finished</Badge> : checkInOpen ? <Badge tone="green">Happening now</Badge> : null}
          </div>
          <h1 className="font-display text-2xl font-bold tracking-tight sm:text-3xl">{event.title}</h1>
          <p className="mt-2 flex flex-wrap items-center gap-x-5 gap-y-1 text-sm text-ink-soft">
            <span className="inline-flex items-center gap-1.5"><Icon name="clock" size={15} /> {fmtDateTime(event.startsAt)} to {fmtTime(event.endsAt)}</span>
            {event.location && <span className="inline-flex items-center gap-1.5"><Icon name="pin" size={15} /> {event.location}</span>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {!finished && !signedUp && (
            <>
              <Button variant="primary" loading={join.isPending} onClick={() => join.mutate('VOLUNTEER')}>Volunteer</Button>
              <Button loading={join.isPending} onClick={() => join.mutate('PARTICIPANT')}>Attend</Button>
            </>
          )}
          {signedUp && !finished && !event.myAttended && checkInOpen && (
            <Button variant="primary" icon="check" loading={checkIn.isPending} onClick={() => checkIn.mutate()}>Check in</Button>
          )}
          {event.myAttended && <Badge tone="green">You are checked in</Badge>}
          {signedUp && !finished && !event.myAttended && <Button onClick={() => leave.mutate()} loading={leave.isPending}>Leave</Button>}
          {can('EVENT_UPDATE') && <IconButton icon="pencil" label="Edit event" onClick={() => setEditing(true)} />}
          {can('EVENT_DELETE') && <IconButton icon="trash" label="Delete event" onClick={onDelete} className="hover:bg-rose-50 hover:text-rose-700" />}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <div className="space-y-6">
          {event.description && (
            <Card className="p-5">
              <p className="whitespace-pre-wrap text-[15px] leading-relaxed text-ink-soft">{event.description}</p>
            </Card>
          )}

          <Section
            title={`Tasks (${tasks.data?.totalElements ?? event.taskCount})`}
            action={
              can('TASK_CREATE') ? (
                <div className="flex gap-2">
                  <Button size="sm" icon="template" onClick={() => setApplying(true)}>From template</Button>
                  <Button size="sm" variant="primary" icon="plus" onClick={() => setAddingTask(true)}>Add task</Button>
                </div>
              ) : undefined
            }
          >
            {tasks.isLoading ? (
              <Loading />
            ) : tasks.data && tasks.data.content.length > 0 ? (
              tasks.data.content.map((t) => <TaskRow key={t.id} task={t} />)
            ) : (
              <EmptyState icon="tasks" title="No tasks for this event yet">
                {can('TASK_CREATE') ? 'Apply a template to create the whole checklist at once.' : 'Tasks assigned to you for this event will show here.'}
              </EmptyState>
            )}
          </Section>
        </div>

        <Section title={`People (${attendees.length})`}>
          <div className="grid grid-cols-3 divide-x divide-chalk-200 border-b border-chalk-200 text-center">
            <Count label="Signed up" value={event.attendeeCount} />
            <Count label="Volunteers" value={event.volunteerCount} />
            <Count label="Checked in" value={event.attendedCount} />
          </div>

          {attendees.length === 0 ? (
            <EmptyState icon="users" title="Nobody yet">Volunteers and attendees appear here.</EmptyState>
          ) : (
            <ul className="divide-y divide-chalk-200">
              {attendees.map((a) => (
                <li key={a.userId} className="flex items-center gap-3 px-5 py-3">
                  <Avatar name={a.name} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{a.name}</p>
                    <p className="text-xs text-ink-mute">{a.participation === 'VOLUNTEER' ? 'Volunteer' : 'Attendee'}{a.checkedInAt ? `, in at ${fmtTime(a.checkedInAt)}` : ''}</p>
                  </div>
                  {canManage ? (
                    <>
                      <Checkbox checked={a.attended} onChange={(v) => setAttended.mutate({ userId: a.userId, attended: v })} label={<span className="text-xs">Present</span>} />
                      <IconButton icon="x" label={`Remove ${a.name}`} onClick={() => removeAttendee.mutate(a.userId)} />
                    </>
                  ) : (
                    a.attended && <Badge tone="green">Present</Badge>
                  )}
                </li>
              ))}
            </ul>
          )}

          {canManage && can('USER_VIEW') && (
            <div className="space-y-2 border-t border-chalk-200 p-4">
              <p className="text-sm font-semibold text-ink-soft">Add someone</p>
              <Select aria-label="Member" value={addUserId} onChange={(e) => setAddUserId(e.target.value)}>
                <option value="">Choose a member</option>
                {addable.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </Select>
              <div className="flex gap-2">
                <Select aria-label="Role" value={addRole} onChange={(e) => setAddRole(e.target.value as Participation)}>
                  <option value="VOLUNTEER">Volunteer</option>
                  <option value="PARTICIPANT">Attendee</option>
                </Select>
                <Button variant="primary" disabled={!addUserId} loading={addAttendee.isPending} onClick={() => addAttendee.mutate()}>Add</Button>
              </div>
            </div>
          )}
        </Section>
      </div>

      <EventFormModal open={editing} onClose={() => setEditing(false)} existing={event} />
      <TaskFormModal open={addingTask} onClose={() => setAddingTask(false)} defaultEventId={id} />

      <Modal
        open={applying}
        onClose={() => setApplying(false)}
        title="Create tasks from a template"
        footer={
          <>
            <Button onClick={() => setApplying(false)}>Cancel</Button>
            <Button variant="primary" disabled={!templateId} loading={apply.isPending} onClick={() => apply.mutate()}>Create tasks</Button>
          </>
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-ink-soft">Each item in the template becomes a task, due the number of days before this event that the template says.</p>
          <Field label="Template">
            <Select value={templateId} onChange={(e) => setTemplateId(e.target.value)}>
              <option value="">Choose a template</option>
              {templates.data?.map((t) => <option key={t.id} value={t.id}>{t.name} ({t.items.length} tasks)</option>)}
            </Select>
          </Field>
          {templates.data?.length === 0 && <p className="text-sm text-ink-mute">There are no templates yet. Create one under Templates and repeats.</p>}
          {can('TASK_ASSIGN') && (
            <Checkbox checked={byDepartment} onChange={setByDepartment} label="Assign each task to everyone in its department" />
          )}
        </div>
      </Modal>
    </>
  );
}

function BackLink() {
  return (
    <Link to="/events" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-ink-mute hover:text-ink">
      <Icon name="chevronLeft" size={16} /> All events
    </Link>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <div className="px-2 py-3.5">
      <p className="font-display text-xl font-bold tabular">{value}</p>
      <p className="text-xs text-ink-mute">{label}</p>
    </div>
  );
}
