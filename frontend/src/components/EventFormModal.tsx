import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useActiveClub } from '@/lib/club';
import { fromLocalInput, toLocalInput } from '@/lib/format';
import type { EventItem } from '@/lib/types';
import { useFeedback } from './feedback';
import { Button, Field, Input, Modal, Notice, Textarea } from './ui';

function defaultStart(): string {
  const d = new Date();
  d.setDate(d.getDate() + 7);
  d.setHours(10, 0, 0, 0);
  return toLocalInput(d.toISOString());
}

export function EventFormModal({
  open,
  onClose,
  existing,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  existing?: EventItem;
  onSaved?: (event: EventItem) => void;
}) {
  const { clubId } = useActiveClub();
  const { toast } = useFeedback();
  const qc = useQueryClient();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [location, setLocation] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    setError(null);
    setFieldErrors({});
    if (existing) {
      setTitle(existing.title);
      setDescription(existing.description ?? '');
      setLocation(existing.location ?? '');
      setStartsAt(toLocalInput(existing.startsAt));
      setEndsAt(toLocalInput(existing.endsAt));
    } else {
      const start = defaultStart();
      setTitle('');
      setDescription('');
      setLocation('');
      setStartsAt(start);
      setEndsAt(toLocalInput(new Date(new Date(start).getTime() + 2 * 3600 * 1000).toISOString()));
    }
  }, [open, existing]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        title: title.trim(),
        description: description.trim() || null,
        location: location.trim() || null,
        startsAt: fromLocalInput(startsAt),
        endsAt: fromLocalInput(endsAt),
        clubId,
      };
      return existing
        ? api<EventItem>(`/api/events/${existing.id}`, { method: 'PUT', body })
        : api<EventItem>('/api/events', { method: 'POST', body });
    },
    onSuccess: (event) => {
      qc.invalidateQueries({ queryKey: ['events'] });
      qc.invalidateQueries({ queryKey: ['event', event.id] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      toast(existing ? 'Event updated' : 'Event created');
      onSaved?.(event);
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
    if (new Date(endsAt) <= new Date(startsAt)) {
      setError('The event must end after it starts.');
      return;
    }
    save.mutate();
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={existing ? 'Edit event' : 'New event'}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={save.isPending} onClick={() => (document.getElementById('event-form') as HTMLFormElement | null)?.requestSubmit()}>
            {existing ? 'Save changes' : 'Create event'}
          </Button>
        </>
      }
    >
      <form id="event-form" onSubmit={submit} className="space-y-4">
        {error && <Notice tone="rose">{error}</Notice>}
        <Field label="Title" error={fieldErrors.title}>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} autoFocus />
        </Field>
        <Field label="Description">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={5000} />
        </Field>
        <Field label="Location">
          <Input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={200} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Starts">
            <Input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} required />
          </Field>
          <Field label="Ends">
            <Input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} required />
          </Field>
        </div>
      </form>
    </Modal>
  );
}
