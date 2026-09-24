import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { fmtDateTime } from '@/lib/format';
import type { EmailLog } from '@/lib/types';
import { Badge, Card, EmptyState, ErrorNote, Loading, PageHeader, Select, type Tone } from '@/components/ui';

const TONE: Record<EmailLog['status'], Tone> = { SENT: 'green', PENDING: 'amber', FAILED: 'rose' };
const LABEL: Record<EmailLog['status'], string> = { SENT: 'Sent', PENDING: 'Waiting', FAILED: 'Failed' };

export function SystemPage() {
  const [status, setStatus] = useState<'' | EmailLog['status']>('');
  const q = useQuery({
    queryKey: ['emails', status],
    queryFn: () => api<EmailLog[]>('/api/system/emails', { query: { status } }),
    refetchInterval: 15_000,
  });

  return (
    <>
      <PageHeader
        title="Email delivery"
        subtitle="The latest 100 emails the system tried to send. Failed emails are retried automatically until they run out of attempts."
      />
      <Card className="mb-4 p-3 sm:p-4">
        <div className="max-w-xs">
          <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}>
            <option value="">All statuses</option>
            <option value="FAILED">Failed</option>
            <option value="PENDING">Waiting to send</option>
            <option value="SENT">Sent</option>
          </Select>
        </div>
      </Card>

      <Card className="overflow-x-auto">
        {q.isLoading ? (
          <Loading />
        ) : q.isError ? (
          <div className="p-4"><ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
        ) : q.data && q.data.length > 0 ? (
          <table className="w-full min-w-[40rem] text-sm">
            <thead>
              <tr className="border-b border-chalk-200 bg-chalk-50 text-left text-ink-mute">
                <th className="px-5 py-2.5 font-semibold">To</th>
                <th className="px-3 py-2.5 font-semibold">Subject</th>
                <th className="px-3 py-2.5 font-semibold">Status</th>
                <th className="px-3 py-2.5 text-right font-semibold">Tries</th>
                <th className="px-5 py-2.5 font-semibold">Queued</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((m) => (
                <tr key={m.id} className="border-b border-chalk-100 align-top last:border-0">
                  <td className="px-5 py-2.5">{m.to}</td>
                  <td className="max-w-xs px-3 py-2.5">
                    <span className="block truncate" title={m.subject}>{m.subject}</span>
                    {m.lastError && <span className="mt-0.5 block text-xs text-rose-700">{m.lastError}</span>}
                  </td>
                  <td className="px-3 py-2.5"><Badge tone={TONE[m.status]}>{LABEL[m.status]}</Badge></td>
                  <td className="px-3 py-2.5 text-right tabular">{m.attempts}</td>
                  <td className="whitespace-nowrap px-5 py-2.5 text-ink-mute tabular">{fmtDateTime(m.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <EmptyState icon="mail" title="No emails here">Emails appear once reminders, assignments or announcements are sent.</EmptyState>
        )}
      </Card>
    </>
  );
}
