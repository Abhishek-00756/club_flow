import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { fromLocalInput, toLocalInput } from '@/lib/format';
import { useDepartments, useEvents, useMembers } from '@/lib/queries';
import type { Priority, TaskDetail } from '@/lib/types';
import { MemberPicker } from './MemberPicker';
import { useFeedback } from './feedback';
import { Button, Field, Input, Modal, Notice, Select, Textarea } from './ui';

function tomorrowEvening(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(18, 0, 0, 0);
  return toLocalInput(d.toISOString());
}

export function TaskFormModal({
  open,
  onClose,
  existing,
  defaultEventId,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  existing?: TaskDetail['task'];
  defaultEventId?: string;
  onSaved?: (detail: TaskDetail) => void;
}) {
  const { can } = useAuth();
  const { clubId } = useActiveClub();
  const { toast } = useFeedback();
  const qc = useQueryClient();
  const departments = useDepartments();
  const events = useEvents(false);
  const members = useMembers(true);

  const canAssign = can('TASK_ASSIGN') && can('USER_VIEW');

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [deadline, setDeadline] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [eventId, setEventId] = useState('');
  const [assignees, setAssignees] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Reset the form each time it opens.
  useEffect(() => {
    if (!open) return;
    setError(null);
    setFieldErrors({});
    if (existing) {
      setTitle(existing.title);
      setDescription(existing.description ?? '');
      setPriority(existing.priority);
      setDeadline(toLocalInput(existing.deadline));
      setDepartmentId(existing.departmentId ?? '');
      setEventId(existing.eventId ?? '');
      setAssignees(existing.assignees.map((a) => a.id));
    } else {
      setTitle('');
      setDescription('');
      setPriority('MEDIUM');
      setDeadline(tomorrowEvening());
      setDepartmentId('');
      setEventId(defaultEventId ?? '');
      setAssignees([]);
    }
  }, [open, existing, defaultEventId]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title: title.trim(),
        description: description.trim(),
        priority,
        deadline: fromLocalInput(deadline),
        departmentId: departmentId || null,
        eventId: eventId || null,
        // Leave assignees untouched when this person cannot change them.
        assigneeIds: canAssign ? assignees : null,
        clubId,
      };
      return existing
        ? api<TaskDetail>(`/api/tasks/${existing.id}`, { method: 'PUT', body })
        : api<TaskDetail>('/api/tasks', { method: 'POST', body });
    },
    onSuccess: (detail) => {
      qc.invalidateQueries({ queryKey: ['tasks'] });
      qc.invalidateQueries({ queryKey: ['task', detail.task.id] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['events'] });
      toast(existing ? 'Task updated' : 'Task created');
      onSaved?.(detail);
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
    if (!deadline) {
      setError('Pick a deadline.');
      return;
    }
    save.mutate();
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={existing ? 'Edit task' : 'New task'}
      wide
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} onClick={() => (document.getElementById('task-form') as HTMLFormElement | null)?.requestSubmit()}>
            {existing ? 'Save changes' : 'Create task'}
          </Button>
        </>
      }
    >
      <form id="task-form" onSubmit={submit} className="space-y-4">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Title" error={fieldErrors.title}>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} autoFocus placeholder="Design the Instagram post" />
        </Field>
        <Field label="Details" hint="What does done look like? Add links or sizes if they matter.">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={5000} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Priority">
            <Select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </Select>
          </Field>
          <Field label="Deadline" error={fieldErrors.deadline}>
            <Input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} required />
          </Field>
          <Field label="Department">
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">None</option>
              {departments.data?.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
          {can('EVENT_VIEW') && (
            <Field label="Event">
              <Select value={eventId} onChange={(e) => setEventId(e.target.value)}>
                <option value="">Not linked to an event</option>
                {events.data?.map((ev) => (
                  <option key={ev.id} value={ev.id}>
                    {ev.title}
                  </option>
                ))}
              </Select>
            </Field>
          )}
        </div>
        {canAssign && (
          <Field label="Assign to" group>
            <MemberPicker members={members.data ?? []} selected={assignees} onChange={setAssignees} loading={members.isLoading} />
          </Field>
        )}
      </form>
    </Modal>
  );
}
