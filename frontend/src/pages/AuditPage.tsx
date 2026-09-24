import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useActiveClub } from '@/lib/club';
import { fmtDateTime, humanizeCode } from '@/lib/format';
import type { AuditLog, PageResponse } from '@/lib/types';
import { Badge, Card, EmptyState, ErrorNote, Loading, PageHeader, Pager, Select } from '@/components/ui';

const ENTITY_TYPES = ['TASK', 'EVENT', 'USER', 'CLUB', 'DEPARTMENT', 'ANNOUNCEMENT', 'TEMPLATE', 'RECURRING_TASK', 'ROLE'];

export function AuditPage() {
  const { clubId } = useActiveClub();
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<string | null>(null);

  const q = useQuery({
    queryKey: ['audit', clubId, entityType, page],
    queryFn: () => api<PageResponse<AuditLog>>('/api/audit', { query: { clubId, entityType, page, size: 30 } }),
    enabled: !!clubId,
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader title="Audit log" subtitle="Who changed what, and when. Entries cannot be edited or deleted." />
      <Card className="mb-4 p-3 sm:p-4">
        <div className="max-w-xs">
          <Select aria-label="Type of record" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
            <option value="">All record types</option>
            {ENTITY_TYPES.map((t) => <option key={t} value={t}>{humanizeCode(t)}</option>)}
          </Select>
        </div>
      </Card>

      <Card>
        {q.isLoading ? (
          <Loading />
        ) : q.isError ? (
          <div className="p-4"><ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
        ) : q.data && q.data.content.length > 0 ? (
          <>
            <ul>
              {q.data.content.map((e) => {
                const hasDetail = !!(e.oldValue || e.newValue);
                return (
                  <li key={e.id} className="border-b border-chalk-200 last:border-0">
                    <button
                      type="button"
                      disabled={!hasDetail}
                      onClick={() => setOpen(open === e.id ? null : e.id)}
                      className="flex w-full flex-wrap items-center gap-x-4 gap-y-1 px-4 py-3 text-left enabled:hover:bg-chalk-50 sm:px-5"
                    >
                      <span className="w-40 flex-none text-sm text-ink-mute tabular">{fmtDateTime(e.createdAt)}</span>
                      <span className="min-w-0 flex-1 basis-56">
                        <span className="text-sm font-semibold">{humanizeCode(e.action)}</span>
                        <span className="text-sm text-ink-mute"> by {e.userName ?? 'the system'}</span>
                      </span>
                      {e.entityType && <Badge>{humanizeCode(e.entityType)}</Badge>}
                      {e.ipAddress && <span className="hidden text-xs text-ink-faint md:inline">{e.ipAddress}</span>}
                    </button>
                    {open === e.id && hasDetail && (
                      <div className="grid gap-3 bg-chalk-50 px-5 pb-4 pt-1 text-xs sm:grid-cols-2">
                        {e.oldValue && <Detail label="Before" value={e.oldValue} />}
                        {e.newValue && <Detail label="After" value={e.newValue} />}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
            <Pager page={q.data.page} totalPages={q.data.totalPages} onChange={setPage} />
          </>
        ) : (
          <EmptyState icon="scroll" title="Nothing recorded yet">Actions like creating tasks or changing roles will be listed here.</EmptyState>
        )}
      </Card>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="mb-1 font-semibold text-ink-mute">{label}</p>
      <pre className="overflow-x-auto whitespace-pre-wrap break-words rounded-md border border-chalk-200 bg-white p-2 font-mono text-[11px] text-ink-soft">{value}</pre>
    </div>
  );
}
