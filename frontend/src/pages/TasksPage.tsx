import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { useDepartments } from '@/lib/queries';
import { PRIORITY_LABEL, STATUS_LABEL } from '@/lib/format';
import type { PageResponse, Priority, Task, TaskStatus } from '@/lib/types';
import { TaskFormModal } from '@/components/TaskFormModal';
import { TaskRow } from '@/components/TaskRow';
import { Button, Card, Checkbox, EmptyState, ErrorNote, Loading, PageHeader, Pager, SearchInput, Select } from '@/components/ui';
import { useDebounced } from '@/lib/hooks';

const PAGE_SIZE = 20;

export function TasksPage() {
  const { can } = useAuth();
  const { clubId } = useActiveClub();
  const departments = useDepartments();
  const [params, setParams] = useSearchParams();

  const [search, setSearch] = useState('');
  const q = useDebounced(search, 300);
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [priority, setPriority] = useState<Priority | ''>('');
  const [departmentId, setDepartmentId] = useState('');
  const [sort, setSort] = useState<'deadline' | 'created' | 'updated'>('deadline');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const canSeeAll = can('TASK_VIEW_ALL');
  const mine = params.get('mine') === '1';
  const overdue = params.get('overdue') === '1';

  function setFlag(key: string, on: boolean) {
    const next = new URLSearchParams(params);
    if (on) next.set(key, '1');
    else next.delete(key);
    setParams(next, { replace: true });
    setPage(0);
  }

  const list = useQuery({
    queryKey: ['tasks', clubId, { q, status, priority, departmentId, sort, page, mine, overdue }],
    queryFn: () =>
      api<PageResponse<Task>>('/api/tasks', {
        query: {
          clubId,
          q,
          status,
          priority,
          departmentId,
          mine: mine || undefined,
          overdue: overdue || undefined,
          sort,
          desc: sort !== 'deadline',
          page,
          size: PAGE_SIZE,
        },
      }),
    enabled: !!clubId,
    placeholderData: keepPreviousData,
  });

  const anyFilter = !!(q || status || priority || departmentId || mine || overdue);
  const reset = (fn: () => void) => () => {
    fn();
    setPage(0);
  };

  return (
    <>
      <PageHeader
        title={canSeeAll ? 'Tasks' : 'My tasks'}
        subtitle={canSeeAll ? 'Everything the club is working on.' : 'Tasks assigned to you.'}
        actions={can('TASK_CREATE') ? <Button variant="primary" icon="plus" onClick={() => setCreating(true)}>New task</Button> : undefined}
      />

      <Card className="mb-4 p-3 sm:p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr_1fr]">
          <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder="Search tasks" />
          <Select aria-label="Status" value={status} onChange={(e) => reset(() => setStatus(e.target.value as TaskStatus | ''))()}>
            <option value="">Any status</option>
            {(Object.keys(STATUS_LABEL) as TaskStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </Select>
          <Select aria-label="Priority" value={priority} onChange={(e) => reset(() => setPriority(e.target.value as Priority | ''))()}>
            <option value="">Any priority</option>
            {(Object.keys(PRIORITY_LABEL) as Priority[]).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
          </Select>
          <Select aria-label="Department" value={departmentId} onChange={(e) => reset(() => setDepartmentId(e.target.value))()}>
            <option value="">Any department</option>
            {departments.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </Select>
          <Select aria-label="Sort by" value={sort} onChange={(e) => reset(() => setSort(e.target.value as typeof sort))()}>
            <option value="deadline">Earliest deadline</option>
            <option value="created">Newest first</option>
            <option value="updated">Recently updated</option>
          </Select>
        </div>
        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2">
          {canSeeAll && <Checkbox checked={mine} onChange={(v) => setFlag('mine', v)} label="Assigned to me" />}
          <Checkbox checked={overdue} onChange={(v) => setFlag('overdue', v)} label="Overdue only" />
        </div>
      </Card>

      <Card>
        {list.isLoading ? (
          <Loading />
        ) : list.isError ? (
          <div className="p-4"><ErrorNote message={errorMessage(list.error)} onRetry={() => list.refetch()} /></div>
        ) : list.data && list.data.content.length > 0 ? (
          <>
            {list.data.content.map((t) => <TaskRow key={t.id} task={t} />)}
            <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        ) : (
          <EmptyState icon="tasks" title={anyFilter ? 'No tasks match those filters' : 'No tasks yet'}>
            {anyFilter ? 'Try clearing a filter.' : can('TASK_CREATE') ? 'Create the first task to get things moving.' : 'Tasks assigned to you will appear here.'}
          </EmptyState>
        )}
      </Card>

      <TaskFormModal open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
