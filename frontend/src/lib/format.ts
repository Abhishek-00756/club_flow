import type { Priority, Role, TaskStatus } from './types';

const dateTime = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  hour: 'numeric',
  minute: '2-digit',
});
const dateTimeYear = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});
const dateOnly = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const shortDate = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });
const timeOnly = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' });

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.getFullYear() === new Date().getFullYear() ? dateTime.format(d) : dateTimeYear.format(d);
}
export const fmtDate = (iso: string | null | undefined) => (iso ? dateOnly.format(new Date(iso)) : '');
export const fmtShortDate = (iso: string | null | undefined) => (iso ? shortDate.format(new Date(iso)) : '');
export const fmtTime = (iso: string | null | undefined) => (iso ? timeOnly.format(new Date(iso)) : '');

/** "in 3 hours", "2 days ago". Coarse on purpose: exact times are shown next to it where they matter. */
export function relative(iso: string): string {
  const diffMs = new Date(iso).getTime() - Date.now();
  const abs = Math.abs(diffMs);
  const minutes = Math.round(abs / 60_000);
  let text: string;
  if (minutes < 1) return 'just now';
  if (minutes < 60) text = `${minutes} min`;
  else if (minutes < 60 * 24) {
    const h = Math.round(minutes / 60);
    text = `${h} ${h === 1 ? 'hour' : 'hours'}`;
  } else {
    const d = Math.round(minutes / (60 * 24));
    text = `${d} ${d === 1 ? 'day' : 'days'}`;
  }
  return diffMs >= 0 ? `in ${text}` : `${text} ago`;
}

export function hoursText(hours: number): string {
  if (hours < 1) return 'less than an hour';
  if (hours < 48) return `${hours} ${hours === 1 ? 'hour' : 'hours'}`;
  const d = Math.floor(hours / 24);
  return `${d} days`;
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** <input type="datetime-local"> works in local time without a zone; convert both ways. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const PRIORITY_LABEL: Record<Priority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  URGENT: 'Urgent',
};

export const ROLE_LABEL: Record<Role, string> = {
  SUPERADMIN: 'Super admin',
  ADMIN: 'Admin',
  SECRETARY: 'Secretary',
  JOINT_SECRETARY: 'Joint secretary',
  MEMBER: 'Member',
};

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** "TASK_STATUS_CHANGED" -> "Task status changed" */
export function humanizeCode(code: string): string {
  const text = code.toLowerCase().replace(/_/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}
