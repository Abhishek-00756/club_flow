import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '@/lib/api';
import { ROLE_LABEL } from '@/lib/format';
import type { PermissionMatrix, Role } from '@/lib/types';
import { Icon } from '@/components/Icon';
import { useFeedback } from '@/components/feedback';
import { Button, Card, ErrorNote, Loading, Notice, PageHeader, cx } from '@/components/ui';

const LABELS: Record<string, string> = {
  TASK_CREATE: 'Create tasks',
  TASK_ASSIGN: 'Assign tasks to people',
  TASK_UPDATE: 'Edit, cancel and reopen any task',
  TASK_DELETE: 'Delete tasks',
  TASK_APPROVE: 'Review, approve and send back work',
  TASK_VIEW_ALL: 'See every task in the club',
  TASK_VIEW_ASSIGNED: 'See tasks assigned to them',
  TASK_UPDATE_ASSIGNED: 'Start work and attach files on their own tasks',
  TASK_SUBMIT: 'Submit their own work for review',
  USER_VIEW: 'See the member list',
  USER_CREATE: 'Add members',
  USER_UPDATE: 'Edit members and reset passwords',
  USER_DELETE: 'Remove members',
  USER_ROLE_UPDATE: 'Change members’ roles',
  EVENT_VIEW: 'See events and sign up',
  EVENT_CREATE: 'Create events',
  EVENT_UPDATE: 'Edit events and manage attendance',
  EVENT_DELETE: 'Delete events',
  ANNOUNCEMENT_CREATE: 'Post announcements',
  TEMPLATE_MANAGE: 'Manage templates and repeating tasks',
  DEPARTMENT_MANAGE: 'Manage departments',
  CLUB_UPDATE: 'Edit club details and the join code',
  CLUB_MANAGE: 'Create and delete clubs',
  REPORT_VIEW: 'See club dashboards and reports',
  AUDIT_VIEW: 'See the audit log',
  SYSTEM_CONFIG: 'Change role permissions, see email delivery',
};

const GROUPS: Array<{ title: string; prefixes: string[] }> = [
  { title: 'Tasks', prefixes: ['TASK_'] },
  { title: 'Members', prefixes: ['USER_'] },
  { title: 'Events', prefixes: ['EVENT_'] },
  { title: 'Club', prefixes: ['ANNOUNCEMENT_', 'TEMPLATE_', 'DEPARTMENT_', 'CLUB_', 'REPORT_'] },
  { title: 'System', prefixes: ['AUDIT_', 'SYSTEM_'] },
];

const EDITABLE: Role[] = ['ADMIN', 'SECRETARY', 'JOINT_SECRETARY', 'MEMBER'];

export function RolesPage() {
  const { toast } = useFeedback();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['permissions'], queryFn: () => api<PermissionMatrix>('/api/permissions') });
  const [draft, setDraft] = useState<Record<string, string[]>>({});

  useEffect(() => {
    if (q.data) setDraft(q.data.roles);
  }, [q.data]);

  const save = useMutation({
    mutationFn: (role: Role) => api<PermissionMatrix>(`/api/permissions/${role}`, { method: 'PUT', body: { permissions: draft[role] } }),
    onSuccess: (data, role) => {
      qc.setQueryData(['permissions'], data);
      toast(`${ROLE_LABEL[role]} permissions saved. People see the change at their next sign-in or refresh.`);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError || !q.data) return <ErrorNote message={errorMessage(q.error)} onRetry={() => q.refetch()} />;

  const saved = q.data.roles;
  const dirty = (role: Role) => JSON.stringify([...(draft[role] ?? [])].sort()) !== JSON.stringify([...(saved[role] ?? [])].sort());

  function toggle(role: Role, permission: string) {
    setDraft((d) => {
      const current = d[role] ?? [];
      return { ...d, [role]: current.includes(permission) ? current.filter((p) => p !== permission) : [...current, permission] };
    });
  }

  const groups = GROUPS.map((g) => ({
    title: g.title,
    items: q.data!.permissions.filter((p) => g.prefixes.some((prefix) => p.startsWith(prefix))),
  })).filter((g) => g.items.length > 0);

  return (
    <>
      <PageHeader title="Roles and permissions" subtitle="Decide what each role can do. Changes apply to every club." />
      <Notice tone="yellow">
        Removing a permission a role relies on, such as seeing tasks, can leave people unable to do their work. Super admins always keep every permission.
      </Notice>

      <Card className="mt-5 overflow-x-auto">
        <table className="w-full min-w-[42rem] text-sm">
          <thead>
            <tr className="border-b border-chalk-200 bg-chalk-50">
              <th className="px-5 py-3 text-left font-semibold text-ink-mute">Permission</th>
              <th className="w-24 px-2 py-3 text-center font-semibold text-ink-mute">Super admin</th>
              {EDITABLE.map((r) => (
                <th key={r} className="w-24 px-2 py-3 text-center font-semibold text-ink">{ROLE_LABEL[r]}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {groups.map((group) => (
              <FragmentRows key={group.title} title={group.title}>
                {group.items.map((p) => (
                  <tr key={p} className="border-b border-chalk-100">
                    <td className="px-5 py-2.5">
                      <span className="font-medium text-ink">{LABELS[p] ?? p}</span>
                      <span className="ml-2 hidden text-xs text-ink-faint lg:inline">{p}</span>
                    </td>
                    <td className="px-2 py-2.5 text-center text-ink-faint"><Icon name="check" size={16} className="mx-auto" /></td>
                    {EDITABLE.map((r) => (
                      <td key={r} className="px-2 py-2.5 text-center">
                        <input
                          type="checkbox"
                          checked={draft[r]?.includes(p) ?? false}
                          onChange={() => toggle(r, p)}
                          aria-label={`${ROLE_LABEL[r]}: ${LABELS[p] ?? p}`}
                          className="h-4 w-4 accent-marker-600"
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </FragmentRows>
            ))}
          </tbody>
          <tfoot>
            <tr className="bg-chalk-50">
              <td className="px-5 py-3 text-ink-mute" />
              <td />
              {EDITABLE.map((r) => (
                <td key={r} className="px-2 py-3 text-center">
                  <Button size="sm" variant={dirty(r) ? 'primary' : 'secondary'} disabled={!dirty(r)} loading={save.isPending && save.variables === r} onClick={() => save.mutate(r)}>
                    Save
                  </Button>
                </td>
              ))}
            </tr>
          </tfoot>
        </table>
      </Card>
    </>
  );
}

function FragmentRows({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <tr>
        <td colSpan={2 + EDITABLE.length} className={cx('bg-chalk-100 px-5 py-2 text-xs font-semibold text-ink-mute')}>
          {title}
        </td>
      </tr>
      {children}
    </>
  );
}
