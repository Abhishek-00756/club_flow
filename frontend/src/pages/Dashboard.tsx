import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { fmtDateTime, fmtShortDate, hoursText, relative, STATUS_LABEL } from '@/lib/format';
import type { ClubDashboard, MemberDashboard, SuperAdminDashboard, TaskStatus } from '@/lib/types';
import { Icon, type IconName } from '@/components/Icon';
import { TaskRow } from '@/components/TaskRow';
import { Avatar, Button, Card, EmptyState, ErrorNote, Loading, PageHeader, Section, cx } from '@/components/ui';

function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

function Stat({
  label,
  value,
  icon,
  alert,
  to,
}: {
  label: string;
  value: number | string;
  icon: IconName;
  alert?: boolean;
  to?: string;
}) {
  const body = (
    <Card className={cx('flex items-center gap-4 p-4', to && 'transition-colors hover:border-chalk-300', alert && 'border-rose-200 bg-rose-50/50')}>
      <span className={cx('flex h-10 w-10 flex-none items-center justify-center rounded-lg', alert ? 'bg-rose-100 text-rose-700' : 'bg-marker-50 text-marker-700')}>
        <Icon name={icon} size={20} />
      </span>
      <div>
        <p className="font-display text-2xl font-bold leading-none tabular text-ink">{value}</p>
        <p className="mt-1 text-sm text-ink-mute">{label}</p>
      </div>
    </Card>
  );
  return to ? <Link to={to}>{body}</Link> : body;
}

export function Dashboard() {
  const { user, can } = useAuth();
  const isSystem = can('CLUB_MANAGE');
  const isClubLead = can('REPORT_VIEW');

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${user?.name.split(' ')[0] ?? ''}`}
        subtitle={new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())}
        actions={
          isClubLead && can('TASK_VIEW_ALL') ? (
            <Link to="/tasks?mine=1">
              <Button icon="tasks">My tasks</Button>
            </Link>
          ) : undefined
        }
      />
      {isSystem && <SystemOverview />}
      {isClubLead ? <ClubOverview /> : <MemberOverview />}
    </>
  );
}

// ---------------------------------------------------------------- member

function MemberOverview() {
  const q = useQuery({ queryKey: ['dashboard', 'member'], queryFn: () => api<MemberDashboard>('/api/dashboard/member') });
  if (q.isLoading) return <Loading />;
  if (q.isError || !q.data) return <ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} />;
  const d = q.data;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Active tasks" value={d.activeTasks} icon="tasks" to="/tasks?mine=1" />
        <Stat label="Due today" value={d.dueToday} icon="clock" />
        <Stat label="Overdue" value={d.overdue} icon="alert" alert={d.overdue > 0} to="/tasks?mine=1&overdue=1" />
        <Stat label="Completed" value={d.completed} icon="check" />
      </div>

      {d.awaitingReview > 0 && (
        <p className="rounded-lg border border-violet-200 bg-violet-50 px-4 py-3 text-sm text-violet-900">
          {d.awaitingReview === 1 ? '1 task is' : `${d.awaitingReview} tasks are`} waiting for review. You will get a notification when it is approved or sent back.
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <Section title="Coming up" action={<Link to="/tasks?mine=1" className="text-sm font-semibold text-marker-700 hover:underline">All my tasks</Link>}>
          {d.upcomingTasks.length === 0 ? (
            <EmptyState icon="check" title="Nothing on your plate">
              New tasks assigned to you will show up here.
            </EmptyState>
          ) : (
            d.upcomingTasks.map((t) => <TaskRow key={t.id} task={t} showAssignees={false} />)
          )}
        </Section>

        <Section title="Upcoming events" action={<Link to="/events" className="text-sm font-semibold text-marker-700 hover:underline">All events</Link>}>
          {d.upcomingEvents.length === 0 ? (
            <EmptyState icon="calendar" title="No events scheduled" />
          ) : (
            <ul>
              {d.upcomingEvents.map((e) => (
                <li key={e.id} className="border-b border-chalk-200 last:border-0">
                  <Link to={`/events/${e.id}`} className="block px-5 py-3.5 hover:bg-chalk-50">
                    <p className="font-semibold text-ink">{e.title}</p>
                    <p className="mt-0.5 text-sm text-ink-mute">
                      {fmtDateTime(e.startsAt)}
                      {e.location ? `, ${e.location}` : ''}
                    </p>
                    {e.myParticipation && <p className="mt-1 text-xs font-semibold text-marker-700">You are signed up as a {e.myParticipation.toLowerCase()}</p>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- club

const STATUS_BAR: Record<TaskStatus, string> = {
  TODO: 'bg-chalk-300',
  IN_PROGRESS: 'bg-sky-500',
  SUBMITTED: 'bg-amber-400',
  UNDER_REVIEW: 'bg-violet-500',
  COMPLETED: 'bg-emerald-500',
  CANCELLED: 'bg-ink-faint/50',
};

function ClubOverview() {
  const { clubId } = useActiveClub();
  const { can } = useAuth();
  const q = useQuery({
    queryKey: ['dashboard', 'club', clubId],
    queryFn: () => api<ClubDashboard>('/api/dashboard/club', { query: { clubId } }),
    enabled: !!clubId,
  });

  if (!clubId) return <EmptyState icon="building" title="Create a club to get started">Club dashboards appear once a club exists.</EmptyState>;
  if (q.isLoading) return <Loading />;
  if (q.isError || !q.data) return <ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} />;
  const d = q.data;
  const statusTotal = Object.values(d.byStatus).reduce((a, b) => a + b, 0);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Stat label="Active members" value={d.members} icon="users" to={can('USER_VIEW') ? '/members' : undefined} />
        <Stat label="Active tasks" value={d.activeTasks} icon="tasks" to="/tasks" />
        <Stat label="Waiting for review" value={d.awaitingReview} icon="inbox" />
        <Stat label="Overdue" value={d.overdue} icon="alert" alert={d.overdue > 0} to="/tasks?overdue=1" />
        <Stat label="Completed" value={d.completed} icon="check" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Section title="Waiting for review">
          {d.awaitingReviewTasks.length === 0 ? (
            <EmptyState icon="check" title="Nothing to review">Submitted work will appear here.</EmptyState>
          ) : (
            <ul>
              {d.awaitingReviewTasks.map((r) => (
                <li key={r.taskId} className="border-b border-chalk-200 last:border-0">
                  <Link to={`/tasks/${r.taskId}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-chalk-50">
                    <span className="min-w-0">
                      <span className="block truncate font-semibold">{r.title}</span>
                      <span className="text-sm text-ink-mute">{r.assignees.map((a) => a.name).join(', ') || 'Unassigned'}</span>
                    </span>
                    {r.submittedAt && <span className="flex-none text-xs text-ink-mute">submitted {relative(r.submittedAt)}</span>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Section>

        <Section title="Overdue">
          {d.overdueTasks.length === 0 ? (
            <EmptyState icon="check" title="Nobody is running late" />
          ) : (
            <ul>
              {d.overdueTasks.map((r, i) => (
                <li key={r.taskId + i} className="border-b border-chalk-200 last:border-0">
                  <Link to={`/tasks/${r.taskId}`} className="flex items-center gap-3 px-5 py-3 hover:bg-chalk-50">
                    {r.assignee ? <Avatar name={r.assignee.name} /> : <span className="flex h-7 w-7 items-center justify-center rounded-full bg-chalk-200 text-ink-mute"><Icon name="user" size={14} /></span>}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold">{r.title}</span>
                      <span className="text-sm text-ink-mute">{r.assignee?.name ?? 'Nobody assigned'}</span>
                    </span>
                    <span className="flex-none text-sm font-semibold text-rose-700">{hoursText(r.hoursOverdue)} late</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Section title="Tasks created and completed, last 8 weeks">
          <WeeklyChart points={d.weekly} />
        </Section>

        <Section title="Where tasks stand">
          <div className="px-5 py-5">
            {statusTotal === 0 ? (
              <p className="text-sm text-ink-mute">No tasks yet.</p>
            ) : (
              <>
                <div className="flex h-3 overflow-hidden rounded-full bg-chalk-200" role="img" aria-label="Task status breakdown">
                  {(Object.keys(d.byStatus) as TaskStatus[]).map((s) =>
                    d.byStatus[s] > 0 ? <div key={s} className={STATUS_BAR[s]} style={{ width: `${(d.byStatus[s] / statusTotal) * 100}%` }} /> : null,
                  )}
                </div>
                <ul className="mt-4 space-y-2 text-sm">
                  {(Object.keys(d.byStatus) as TaskStatus[]).map((s) => (
                    <li key={s} className="flex items-center justify-between">
                      <span className="flex items-center gap-2 text-ink-soft">
                        <span className={cx('h-2.5 w-2.5 rounded-sm', STATUS_BAR[s])} />
                        {STATUS_LABEL[s]}
                      </span>
                      <span className="font-semibold tabular">{d.byStatus[s]}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>
        </Section>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Section title="Departments">
          {d.departments.length === 0 ? (
            <EmptyState icon="building" title="No departments" />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-chalk-200 text-left text-ink-mute">
                  <th className="px-5 py-2.5 font-semibold">Department</th>
                  <th className="px-3 py-2.5 text-right font-semibold">Active</th>
                  <th className="px-3 py-2.5 text-right font-semibold">Done</th>
                  <th className="px-5 py-2.5 text-right font-semibold">Overdue</th>
                </tr>
              </thead>
              <tbody>
                {d.departments.map((x) => (
                  <tr key={x.id} className="border-b border-chalk-100 last:border-0">
                    <td className="px-5 py-2.5 font-semibold">{x.name}</td>
                    <td className="px-3 py-2.5 text-right tabular">{x.active}</td>
                    <td className="px-3 py-2.5 text-right tabular">{x.completed}</td>
                    <td className={cx('px-5 py-2.5 text-right tabular', x.overdue > 0 && 'font-semibold text-rose-700')}>{x.overdue}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>

        <Section title="Member workload">
          {d.workload.length === 0 ? (
            <EmptyState icon="users" title="No members yet" />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-chalk-200 text-left text-ink-mute">
                  <th className="px-5 py-2.5 font-semibold">Member</th>
                  <th className="px-3 py-2.5 text-right font-semibold">Open</th>
                  <th className="px-3 py-2.5 text-right font-semibold">In review</th>
                  <th className="px-5 py-2.5 text-right font-semibold">Overdue</th>
                </tr>
              </thead>
              <tbody>
                {d.workload.map((m) => (
                  <tr key={m.userId} className="border-b border-chalk-100 last:border-0">
                    <td className="px-5 py-2.5">
                      <span className="flex items-center gap-2.5">
                        <Avatar name={m.name} size={24} />
                        <span className="font-semibold">{m.name}</span>
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-right tabular">{m.open}</td>
                    <td className="px-3 py-2.5 text-right tabular">{m.inReview}</td>
                    <td className={cx('px-5 py-2.5 text-right tabular', m.overdue > 0 && 'font-semibold text-rose-700')}>{m.overdue}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>
      </div>
    </div>
  );
}

function WeeklyChart({ points }: { points: ClubDashboard['weekly'] }) {
  const max = Math.max(4, ...points.flatMap((p) => [p.created, p.completed]));
  const W = 520;
  const H = 180;
  const top = 12;
  const bottom = 30;
  const plotH = H - top - bottom;
  const slot = W / Math.max(points.length, 1);
  const bar = Math.min(16, slot / 3);

  return (
    <div className="px-5 py-5">
      <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Tasks created and completed per week">
        {[0, 0.5, 1].map((f) => (
          <g key={f}>
            <line x1={0} x2={W} y1={top + plotH * (1 - f)} y2={top + plotH * (1 - f)} stroke="#E1E5DD" strokeWidth={1} />
            <text x={0} y={top + plotH * (1 - f) - 3} fontSize={10} fill="#98A3AC">
              {Math.round(max * f)}
            </text>
          </g>
        ))}
        {points.map((p, i) => {
          const cx0 = slot * i + slot / 2;
          const h1 = (p.created / max) * plotH;
          const h2 = (p.completed / max) * plotH;
          return (
            <g key={p.weekStart}>
              <rect x={cx0 - bar - 1} y={top + plotH - h1} width={bar} height={h1} rx={2} fill="#CBD1C6">
                <title>{`${p.created} created, week of ${fmtShortDate(p.weekStart + 'T00:00:00')}`}</title>
              </rect>
              <rect x={cx0 + 1} y={top + plotH - h2} width={bar} height={h2} rx={2} fill="#0F7566">
                <title>{`${p.completed} completed, week of ${fmtShortDate(p.weekStart + 'T00:00:00')}`}</title>
              </rect>
              <text x={cx0} y={H - 10} fontSize={10} textAnchor="middle" fill="#65737F">
                {fmtShortDate(p.weekStart + 'T00:00:00')}
              </text>
            </g>
          );
        })}
      </svg>
      <div className="mt-2 flex gap-5 text-xs text-ink-mute">
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-chalk-300" /> Created</span>
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-marker-600" /> Completed</span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- system

function SystemOverview() {
  const { setClubId } = useActiveClub();
  const q = useQuery({ queryKey: ['dashboard', 'system'], queryFn: () => api<SuperAdminDashboard>('/api/dashboard/superadmin') });
  if (q.isLoading) return <Loading />;
  if (q.isError || !q.data) return <ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} />;
  const d = q.data;

  return (
    <div className="mb-8 space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Clubs" value={d.clubs} icon="building" />
        <Stat label="People" value={d.users} icon="users" />
        <Stat label="Active tasks" value={d.activeTasks} icon="tasks" />
        <Stat label="Overdue" value={d.overdueTasks} icon="alert" alert={d.overdueTasks > 0} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <Section title="Clubs">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-chalk-200 text-left text-ink-mute">
                <th className="px-5 py-2.5 font-semibold">Club</th>
                <th className="px-3 py-2.5 text-right font-semibold">Members</th>
                <th className="px-3 py-2.5 text-right font-semibold">Active</th>
                <th className="px-3 py-2.5 text-right font-semibold">Done</th>
                <th className="px-5 py-2.5 text-right font-semibold">Overdue</th>
              </tr>
            </thead>
            <tbody>
              {d.clubStats.map((c) => (
                <tr key={c.id} className="border-b border-chalk-100 last:border-0">
                  <td className="px-5 py-2.5">
                    <button type="button" className="font-semibold text-marker-700 hover:underline" onClick={() => setClubId(c.id)}>
                      {c.name}
                    </button>
                  </td>
                  <td className="px-3 py-2.5 text-right tabular">{c.members}</td>
                  <td className="px-3 py-2.5 text-right tabular">{c.activeTasks}</td>
                  <td className="px-3 py-2.5 text-right tabular">{c.completed}</td>
                  <td className={cx('px-5 py-2.5 text-right tabular', c.overdue > 0 && 'font-semibold text-rose-700')}>{c.overdue}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Section>

        <Section title="Email delivery" action={<Link to="/system" className="text-sm font-semibold text-marker-700 hover:underline">Details</Link>}>
          <dl className="space-y-3 px-5 py-5 text-sm">
            <div className="flex justify-between"><dt className="text-ink-mute">Sent</dt><dd className="font-semibold tabular">{d.emailsSent}</dd></div>
            <div className="flex justify-between"><dt className="text-ink-mute">Waiting to send</dt><dd className="font-semibold tabular">{d.emailsPending}</dd></div>
            <div className="flex justify-between"><dt className="text-ink-mute">Failed</dt><dd className={cx('font-semibold tabular', d.emailsFailed > 0 && 'text-rose-700')}>{d.emailsFailed}</dd></div>
          </dl>
        </Section>
      </div>
      <p className="text-sm text-ink-mute">The overview below is for the club selected at the top. Click a club name above to switch.</p>
    </div>
  );
}
