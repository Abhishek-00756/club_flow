import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { fmtDateTime, relative } from '@/lib/format';
import type { Announcement, PageResponse } from '@/lib/types';
import { useFeedback } from '@/components/feedback';
import { Avatar, Button, Card, EmptyState, ErrorNote, Field, IconButton, Input, Loading, Modal, PageHeader, Pager, Textarea } from '@/components/ui';

export function AnnouncementsPage() {
  const { user, can } = useAuth();
  const { clubId } = useActiveClub();
  const { toast, confirm } = useFeedback();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [composing, setComposing] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  const list = useQuery({
    queryKey: ['announcements', clubId, page],
    queryFn: () => api<PageResponse<Announcement>>('/api/announcements', { query: { clubId, page, size: 10 } }),
    enabled: !!clubId,
    placeholderData: keepPreviousData,
  });

  const create = useMutation({
    mutationFn: () => api<Announcement>('/api/announcements', { method: 'POST', body: { title: title.trim(), body: body.trim(), clubId } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['announcements'] });
      toast('Announcement sent to the club');
      setComposing(false);
      setTitle('');
      setBody('');
      setPage(0);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/announcements/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['announcements'] });
      toast('Announcement removed');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  async function onDelete(a: Announcement) {
    const ok = await confirm({ title: 'Remove this announcement?', message: `“${a.title}” disappears for everyone. Notifications already sent stay in people’s inboxes.`, confirmLabel: 'Remove', danger: true });
    if (ok) remove.mutate(a.id);
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate();
  }

  return (
    <>
      <PageHeader
        title="Announcements"
        subtitle="News for the whole club. Everyone gets a notification, and an email if they have that turned on."
        actions={can('ANNOUNCEMENT_CREATE') ? <Button variant="primary" icon="megaphone" onClick={() => setComposing(true)}>New announcement</Button> : undefined}
      />

      {list.isLoading ? (
        <Loading />
      ) : list.isError ? (
        <ErrorNote message={errorMessage(list.error)} onRetry={() => list.refetch()} />
      ) : list.data && list.data.content.length > 0 ? (
        <div className="space-y-4">
          {list.data.content.map((a) => (
            <Card key={a.id} className="p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <Avatar name={a.author.name} size={34} />
                  <div>
                    <p className="text-sm font-semibold">{a.author.name}</p>
                    <p className="text-xs text-ink-mute" title={fmtDateTime(a.createdAt)}>{relative(a.createdAt)}</p>
                  </div>
                </div>
                {can('ANNOUNCEMENT_CREATE') && (a.author.id === user?.id || can('CLUB_UPDATE')) && (
                  <IconButton icon="trash" label="Remove announcement" onClick={() => onDelete(a)} className="hover:bg-rose-50 hover:text-rose-700" />
                )}
              </div>
              <h2 className="mt-4 font-display text-lg font-semibold">{a.title}</h2>
              <p className="mt-1.5 whitespace-pre-wrap text-[15px] leading-relaxed text-ink-soft">{a.body}</p>
            </Card>
          ))}
          <Card><Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} /></Card>
        </div>
      ) : (
        <Card>
          <EmptyState icon="megaphone" title="No announcements yet">
            {can('ANNOUNCEMENT_CREATE') ? 'Post the first one to let everyone know what is coming up.' : 'When the club posts news it will show up here.'}
          </EmptyState>
        </Card>
      )}

      <Modal
        open={composing}
        onClose={() => setComposing(false)}
        title="New announcement"
        footer={
          <>
            <Button onClick={() => setComposing(false)}>Cancel</Button>
            <Button variant="primary" loading={create.isPending} disabled={!title.trim() || !body.trim()} onClick={() => (document.getElementById('announcement-form') as HTMLFormElement | null)?.requestSubmit()}>
              Send to everyone
            </Button>
          </>
        }
      >
        <form id="announcement-form" onSubmit={submit} className="space-y-4">
          <Field label="Title">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required autoFocus />
          </Field>
          <Field label="Message">
            <Textarea value={body} onChange={(e) => setBody(e.target.value)} maxLength={5000} required className="min-h-[140px]" />
          </Field>
        </form>
      </Modal>
    </>
  );
}
