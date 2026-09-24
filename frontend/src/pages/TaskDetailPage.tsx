import { useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, downloadFile, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fileSize, fmtDateTime, relative } from '@/lib/format';
import { useMembers } from '@/lib/queries';
import type { TaskDetail } from '@/lib/types';
import { Icon } from '@/components/Icon';
import { MemberPicker } from '@/components/MemberPicker';
import { NoteModal } from '@/components/NoteModal';
import { TaskFormModal } from '@/components/TaskFormModal';
import { Deadline, PriorityMark, StatusBadge } from '@/components/TaskBits';
import { useFeedback } from '@/components/feedback';
import { Avatar, Button, Card, EmptyState, ErrorNote, IconButton, Loading, Modal, Notice, Section, Textarea } from '@/components/ui';

type NoteKind = 'submit' | 'approve' | 'reject' | 'cancel';

export function TaskDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user, can } = useAuth();
  const { toast, confirm } = useFeedback();
  const fileInput = useRef<HTMLInputElement>(null);

  const [editing, setEditing] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [assigneeDraft, setAssigneeDraft] = useState<string[]>([]);
  const [noteKind, setNoteKind] = useState<NoteKind | null>(null);
  const [comment, setComment] = useState('');
  const members = useMembers(true);

  const query = useQuery({ queryKey: ['task', id], queryFn: () => api<TaskDetail>(`/api/tasks/${id}`) });

  function accept(detail: TaskDetail) {
    qc.setQueryData(['task', id], detail);
    qc.invalidateQueries({ queryKey: ['tasks'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
    qc.invalidateQueries({ queryKey: ['notifications'] });
  }

  const act = useMutation({
    mutationFn: (v: { path: string; body?: unknown; message: string }) =>
      api<TaskDetail>(`/api/tasks/${id}/${v.path}`, { method: 'POST', body: v.body }),
    onSuccess: (detail, v) => {
      accept(detail);
      toast(v.message);
      setNoteKind(null);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const assign = useMutation({
    mutationFn: () => api<TaskDetail>(`/api/tasks/${id}/assignees`, { method: 'PUT', body: { userIds: assigneeDraft } }),
    onSuccess: (detail) => {
      accept(detail);
      toast('Assignees updated');
      setAssigning(false);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const postComment = useMutation({
    mutationFn: () => api<TaskDetail>(`/api/tasks/${id}/comments`, { method: 'POST', body: { body: comment.trim() } }),
    onSuccess: (detail) => {
      accept(detail);
      setComment('');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api<TaskDetail>(`/api/tasks/${id}/attachments`, { method: 'POST', form });
    },
    onSuccess: (detail) => {
      accept(detail);
      toast('File added');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const removeFile = useMutation({
    mutationFn: (attachmentId: string) => api<TaskDetail>(`/api/tasks/${id}/attachments/${attachmentId}`, { method: 'DELETE' }),
    onSuccess: (detail) => {
      accept(detail);
      toast('File removed');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const remove = useMutation({
    mutationFn: () => api(`/api/tasks/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tasks'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      toast('Task deleted');
      navigate('/tasks', { replace: true });
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  if (query.isLoading) return <Loading />;
  if (query.isError || !query.data) {
    return (
      <>
        <BackLink />
        <EmptyState icon="search" title="Task not found">
          {errorMessage(query.error)}
        </EmptyState>
      </>
    );
  }

  const { task, comments, attachments, actions } = query.data;

  async function onDelete() {
    const ok = await confirm({
      title: 'Delete this task?',
      message: 'The task, its comments and its files are removed for everyone. This cannot be undone. If you only want to stop the work, cancel it instead.',
      confirmLabel: 'Delete task',
      danger: true,
    });
    if (ok) remove.mutate();
  }

  async function onReopen() {
    const ok = await confirm({
      title: 'Reopen this task?',
      message: 'It goes back to “To do” for the assignees, and reminders start again for the current deadline.',
      confirmLabel: 'Reopen',
    });
    if (ok) act.mutate({ path: 'reopen', message: 'Task reopened' });
  }

  const noteConfig: Record<NoteKind, { title: string; description?: string; label: string; placeholder?: string; required?: boolean; confirmLabel: string; danger?: boolean; path: string; message: string; field: string }> = {
    submit: {
      title: 'Submit for review',
      description: 'The reviewer is notified. You can add a note about what you did or where to find it.',
      label: 'Note (optional)',
      placeholder: 'Final files are attached. I used the new logo.',
      confirmLabel: 'Submit',
      path: 'submit',
      message: 'Submitted for review',
      field: 'note',
    },
    approve: {
      title: 'Approve this work',
      label: 'Feedback (optional)',
      placeholder: 'Looks great, thanks.',
      confirmLabel: 'Approve',
      path: 'approve',
      message: 'Task approved',
      field: 'note',
    },
    reject: {
      title: 'Request changes',
      description: 'The task goes back to in progress and the assignees see your reason.',
      label: 'What needs to change?',
      placeholder: 'Please make the date larger and add the venue.',
      required: true,
      confirmLabel: 'Send back',
      path: 'reject',
      message: 'Sent back for changes',
      field: 'note',
    },
    cancel: {
      title: 'Cancel this task',
      description: 'Assignees are told it was cancelled. You can reopen it later.',
      label: 'Reason (optional)',
      confirmLabel: 'Cancel task',
      danger: true,
      path: 'cancel',
      message: 'Task cancelled',
      field: 'note',
    },
  };
  const cfg = noteKind ? noteConfig[noteKind] : null;

  return (
    <>
      <BackLink />

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="mb-2 flex flex-wrap items-center gap-3">
            <StatusBadge status={task.status} />
            <PriorityMark priority={task.priority} />
            {task.overdue && <span className="text-sm font-semibold text-rose-700">Overdue</span>}
          </div>
          <h1 className="font-display text-2xl font-bold tracking-tight text-ink sm:text-3xl">{task.title}</h1>
          <div className="mt-2"><Deadline task={task} /></div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {actions.start && <Button variant="primary" icon="play" loading={act.isPending} onClick={() => act.mutate({ path: 'start', message: 'Task started' })}>Start working</Button>}
          {actions.submit && <Button variant="primary" icon="send" onClick={() => setNoteKind('submit')}>Submit for review</Button>}
          {actions.startReview && <Button icon="inbox" loading={act.isPending} onClick={() => act.mutate({ path: 'review', message: 'Review started' })}>Start review</Button>}
          {actions.review && <Button variant="primary" icon="check" onClick={() => setNoteKind('approve')}>Approve</Button>}
          {actions.review && <Button icon="undo" onClick={() => setNoteKind('reject')}>Request changes</Button>}
          {actions.reopen && <Button icon="refresh" onClick={onReopen}>Reopen</Button>}
          {actions.edit && <IconButton icon="pencil" label="Edit task" onClick={() => setEditing(true)} />}
          {actions.cancel && <IconButton icon="ban" label="Cancel task" onClick={() => setNoteKind('cancel')} />}
          {actions.delete && <IconButton icon="trash" label="Delete task" onClick={onDelete} className="hover:bg-rose-50 hover:text-rose-700" />}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.7fr_1fr]">
        <div className="space-y-6">
          {task.reviewNote && task.status === 'IN_PROGRESS' && (
            <Notice tone="yellow">
              <p className="font-semibold">Changes requested</p>
              <p className="mt-1 whitespace-pre-wrap">{task.reviewNote}</p>
            </Notice>
          )}

          <Section title="Details">
            <div className="px-5 py-4">
              {task.description ? (
                <p className="whitespace-pre-wrap text-[15px] leading-relaxed text-ink-soft">{task.description}</p>
              ) : (
                <p className="text-sm text-ink-mute">No description added.</p>
              )}
            </div>
          </Section>

          <Section title={`Comments (${comments.length})`}>
            <div className="divide-y divide-chalk-200">
              {comments.length === 0 && <p className="px-5 py-5 text-sm text-ink-mute">No comments yet. Use this space for questions and updates.</p>}
              {comments.map((c) => (
                <div key={c.id} className="flex gap-3 px-5 py-4">
                  <Avatar name={c.author.name} size={30} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm">
                      <span className="font-semibold text-ink">{c.author.name}</span>
                      <span className="ml-2 text-xs text-ink-mute" title={fmtDateTime(c.createdAt)}>{relative(c.createdAt)}</span>
                    </p>
                    <p className="mt-1 whitespace-pre-wrap text-sm text-ink-soft">{c.body}</p>
                  </div>
                </div>
              ))}
            </div>
            <form
              className="border-t border-chalk-200 p-4"
              onSubmit={(e) => {
                e.preventDefault();
                if (comment.trim()) postComment.mutate();
              }}
            >
              <Textarea value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Write a comment" maxLength={4000} aria-label="Comment" className="min-h-[72px]" />
              <div className="mt-2 flex justify-end">
                <Button type="submit" variant="primary" size="sm" icon="send" disabled={!comment.trim()} loading={postComment.isPending}>Comment</Button>
              </div>
            </form>
          </Section>
        </div>

        <div className="space-y-6">
          <Card>
            <dl className="divide-y divide-chalk-200 text-sm">
              <Row label="Created by">{task.createdBy.name}</Row>
              <Row label="Department">{task.departmentName ?? 'None'}</Row>
              <Row label="Event">
                {task.eventId ? <Link to={`/events/${task.eventId}`} className="font-semibold text-marker-700 hover:underline">{task.eventTitle}</Link> : 'None'}
              </Row>
              <Row label="Created">{fmtDateTime(task.createdAt)}</Row>
              {task.submittedAt && <Row label="Submitted">{fmtDateTime(task.submittedAt)}</Row>}
              {task.completedAt && <Row label="Completed">{fmtDateTime(task.completedAt)}</Row>}
            </dl>
          </Card>

          <Section
            title="Assigned to"
            action={
              actions.assign && can('USER_VIEW') ? (
                <button
                  type="button"
                  className="text-sm font-semibold text-marker-700 hover:underline"
                  onClick={() => {
                    setAssigneeDraft(task.assignees.map((a) => a.id));
                    setAssigning(true);
                  }}
                >
                  Change
                </button>
              ) : undefined
            }
          >
            {task.assignees.length === 0 ? (
              <p className="px-5 py-4 text-sm text-ink-mute">Nobody yet.</p>
            ) : (
              <ul className="divide-y divide-chalk-200">
                {task.assignees.map((a) => (
                  <li key={a.id} className="flex items-center gap-3 px-5 py-3">
                    <Avatar name={a.name} />
                    <span className="text-sm font-semibold">{a.name}{a.id === user?.id && <span className="ml-1.5 font-normal text-ink-mute">(you)</span>}</span>
                  </li>
                ))}
              </ul>
            )}
          </Section>

          <Section
            title={`Files (${attachments.length})`}
            action={
              actions.attach ? (
                <>
                  <input
                    ref={fileInput}
                    type="file"
                    hidden
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) upload.mutate(f);
                      e.target.value = '';
                    }}
                  />
                  <Button size="sm" icon="paperclip" loading={upload.isPending} onClick={() => fileInput.current?.click()}>Add file</Button>
                </>
              ) : undefined
            }
          >
            {attachments.length === 0 ? (
              <p className="px-5 py-4 text-sm text-ink-mute">No files attached.</p>
            ) : (
              <ul className="divide-y divide-chalk-200">
                {attachments.map((a) => (
                  <li key={a.id} className="flex items-center gap-3 px-5 py-3">
                    <Icon name="paperclip" size={16} className="flex-none text-ink-mute" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{a.fileName}</p>
                      <p className="text-xs text-ink-mute">{fileSize(a.sizeBytes)}, {a.uploadedBy.name}</p>
                    </div>
                    <IconButton
                      icon="download"
                      label={`Download ${a.fileName}`}
                      onClick={() => downloadFile(`/api/tasks/${id}/attachments/${a.id}`, a.fileName).catch((err) => toast(errorMessage(err), 'error'))}
                    />
                    {actions.attach && (a.uploadedBy.id === user?.id || can('TASK_UPDATE')) && (
                      <IconButton icon="trash" label={`Remove ${a.fileName}`} onClick={() => removeFile.mutate(a.id)} className="hover:bg-rose-50 hover:text-rose-700" />
                    )}
                  </li>
                ))}
              </ul>
            )}
            {actions.attach && <p className="border-t border-chalk-200 px-5 py-2.5 text-xs text-ink-mute">Up to 10 MB per file.</p>}
          </Section>
        </div>
      </div>

      <TaskFormModal open={editing} onClose={() => setEditing(false)} existing={task} onSaved={(d) => qc.setQueryData(['task', id], d)} />

      {cfg && (
        <NoteModal
          open
          title={cfg.title}
          description={cfg.description}
          label={cfg.label}
          placeholder={cfg.placeholder}
          required={cfg.required}
          confirmLabel={cfg.confirmLabel}
          danger={cfg.danger}
          busy={act.isPending}
          onClose={() => setNoteKind(null)}
          onSubmit={(note) => act.mutate({ path: cfg.path, body: { [cfg.field]: note }, message: cfg.message })}
        />
      )}

      <Modal
        open={assigning}
        onClose={() => setAssigning(false)}
        title="Assign task"
        footer={
          <>
            <Button onClick={() => setAssigning(false)}>Cancel</Button>
            <Button variant="primary" loading={assign.isPending} onClick={() => assign.mutate()}>Save</Button>
          </>
        }
      >
        <MemberPicker members={members.data ?? []} selected={assigneeDraft} onChange={setAssigneeDraft} loading={members.isLoading} />
      </Modal>
    </>
  );
}

function BackLink() {
  return (
    <Link to="/tasks" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-ink-mute hover:text-ink">
      <Icon name="chevronLeft" size={16} /> All tasks
    </Link>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 px-5 py-3">
      <dt className="text-ink-mute">{label}</dt>
      <dd className="text-right font-medium text-ink">{children}</dd>
    </div>
  );
}
