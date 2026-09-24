import { Badge, cx, type Tone } from './ui';
import { Icon } from './Icon';
import { PRIORITY_LABEL, STATUS_LABEL, fmtDateTime, relative } from '@/lib/format';
import type { Priority, Task, TaskStatus } from '@/lib/types';

const STATUS_TONE: Record<TaskStatus, Tone> = {
  TODO: 'neutral',
  IN_PROGRESS: 'sky',
  SUBMITTED: 'amber',
  UNDER_REVIEW: 'violet',
  COMPLETED: 'green',
  CANCELLED: 'neutral',
};

export function StatusBadge({ status }: { status: TaskStatus }) {
  return (
    <Badge tone={STATUS_TONE[status]} className={status === 'CANCELLED' ? 'line-through decoration-ink-faint' : ''}>
      {STATUS_LABEL[status]}
    </Badge>
  );
}

const PRIORITY_STYLE: Record<Priority, string> = {
  LOW: 'text-ink-mute',
  MEDIUM: 'text-ink-soft',
  HIGH: 'text-orange-700',
  URGENT: 'text-rose-700',
};

export function PriorityMark({ priority }: { priority: Priority }) {
  return (
    <span className={cx('inline-flex items-center gap-1 text-xs font-semibold', PRIORITY_STYLE[priority])}>
      <Icon name="flag" size={13} />
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

/** Shows the deadline, and says so plainly when it has been missed or is close. */
export function Deadline({ task, showRelative = true }: { task: Pick<Task, 'deadline' | 'status' | 'overdue'>; showRelative?: boolean }) {
  const open = task.status === 'TODO' || task.status === 'IN_PROGRESS';
  const soon = open && !task.overdue && new Date(task.deadline).getTime() - Date.now() < 48 * 3600 * 1000;
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 text-sm tabular',
        task.overdue ? 'font-semibold text-rose-700' : soon ? 'font-medium text-amber-800' : 'text-ink-soft',
      )}
    >
      <Icon name="clock" size={14} />
      {fmtDateTime(task.deadline)}
      {showRelative && open && <span className="text-xs font-normal opacity-80">({relative(task.deadline)})</span>}
    </span>
  );
}
