import { Link } from 'react-router-dom';
import type { Task } from '@/lib/types';
import { Icon } from './Icon';
import { Deadline, PriorityMark, StatusBadge } from './TaskBits';
import { AvatarStack } from './ui';

/** One task as a list row: used on the task list, dashboards and event pages. */
export function TaskRow({ task, showAssignees = true }: { task: Task; showAssignees?: boolean }) {
  return (
    <Link
      to={`/tasks/${task.id}`}
      className="group flex flex-col gap-2 border-b border-chalk-200 px-4 py-3.5 last:border-0 hover:bg-chalk-50 sm:flex-row sm:items-center sm:gap-4 sm:px-5"
    >
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-ink group-hover:text-marker-700">{task.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-mute">
          <PriorityMark priority={task.priority} />
          {task.departmentName && <span>{task.departmentName}</span>}
          {task.eventTitle && (
            <span className="inline-flex items-center gap-1">
              <Icon name="calendar" size={12} /> {task.eventTitle}
            </span>
          )}
          {task.commentCount > 0 && <span>{task.commentCount} comments</span>}
          {task.attachmentCount > 0 && (
            <span className="inline-flex items-center gap-1">
              <Icon name="paperclip" size={12} /> {task.attachmentCount}
            </span>
          )}
        </div>
      </div>
      <div className="flex items-center justify-between gap-4 sm:justify-end">
        <Deadline task={task} showRelative={false} />
        {showAssignees && task.assignees.length > 0 && <AvatarStack names={task.assignees.map((a) => a.name)} />}
        <span className="w-[6.5rem] text-right">
          <StatusBadge status={task.status} />
        </span>
      </div>
    </Link>
  );
}
