// Mirrors the backend DTOs. Dates arrive as ISO-8601 strings.

export type Role = 'SUPERADMIN' | 'ADMIN' | 'SECRETARY' | 'JOINT_SECRETARY' | 'MEMBER';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'SUBMITTED' | 'UNDER_REVIEW' | 'COMPLETED' | 'CANCELLED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type Participation = 'VOLUNTEER' | 'PARTICIPANT';
export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface UserRef {
  id: string;
  name: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  clubId: string | null;
  clubName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  active: boolean;
  emailNotifications: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  permissions: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: User;
}

export interface Session {
  id: string;
  createdAt: string;
  expiresAt: string;
  userAgent: string | null;
  ipAddress: string | null;
}

export interface Club {
  id: string;
  name: string;
  description: string | null;
  joinCode: string;
  active: boolean;
  memberCount: number;
  createdAt: string;
}

export interface Department {
  id: string;
  name: string;
  clubId: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface Task {
  id: string;
  title: string;
  description: string | null;
  priority: Priority;
  status: TaskStatus;
  deadline: string;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  completedAt: string | null;
  reviewNote: string | null;
  overdue: boolean;
  clubId: string;
  departmentId: string | null;
  departmentName: string | null;
  eventId: string | null;
  eventTitle: string | null;
  createdBy: UserRef;
  assignees: UserRef[];
  commentCount: number;
  attachmentCount: number;
}

export interface TaskComment {
  id: string;
  body: string;
  author: UserRef;
  createdAt: string;
}

export interface Attachment {
  id: string;
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  uploadedBy: UserRef;
  createdAt: string;
}

export interface TaskActions {
  edit: boolean;
  assign: boolean;
  start: boolean;
  submit: boolean;
  startReview: boolean;
  review: boolean;
  cancel: boolean;
  reopen: boolean;
  delete: boolean;
  attach: boolean;
}

export interface TaskDetail {
  task: Task;
  comments: TaskComment[];
  attachments: Attachment[];
  actions: TaskActions;
}

export interface TaskRequest {
  title: string;
  description: string;
  priority: Priority;
  deadline: string;
  departmentId: string | null;
  eventId: string | null;
  assigneeIds: string[] | null;
  clubId?: string | null;
}

export interface EventItem {
  id: string;
  clubId: string;
  title: string;
  description: string | null;
  location: string | null;
  startsAt: string;
  endsAt: string;
  createdBy: UserRef;
  attendeeCount: number;
  volunteerCount: number;
  attendedCount: number;
  taskCount: number;
  myParticipation: Participation | null;
  myAttended: boolean;
}

export interface Attendee {
  userId: string;
  name: string;
  participation: Participation;
  attended: boolean;
  checkedInAt: string | null;
}

export interface EventDetail {
  event: EventItem;
  attendees: Attendee[];
}

export interface Announcement {
  id: string;
  clubId: string;
  title: string;
  body: string;
  author: UserRef;
  createdAt: string;
}

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  message: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface TemplateItem {
  id: string;
  title: string;
  description: string | null;
  priority: Priority;
  departmentId: string | null;
  departmentName: string | null;
  daysBeforeEvent: number;
}

export interface Template {
  id: string;
  clubId: string;
  name: string;
  description: string | null;
  items: TemplateItem[];
}

export interface RecurringTask {
  id: string;
  clubId: string;
  title: string;
  description: string | null;
  priority: Priority;
  frequency: Frequency;
  dayOfWeek: number | null;
  dayOfMonth: number | null;
  dueInDays: number;
  dueTime: string;
  departmentId: string | null;
  departmentName: string | null;
  active: boolean;
  lastRunOn: string | null;
  assignees: UserRef[];
}

export interface AuditLog {
  id: string;
  clubId: string | null;
  userId: string | null;
  userName: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface PermissionMatrix {
  permissions: string[];
  roles: Record<string, string[]>;
}

export interface EmailLog {
  id: string;
  to: string;
  subject: string;
  status: 'PENDING' | 'SENT' | 'FAILED';
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

// ---- dashboards ----

export interface MemberDashboard {
  activeTasks: number;
  dueToday: number;
  completed: number;
  overdue: number;
  awaitingReview: number;
  upcomingTasks: Task[];
  upcomingEvents: EventItem[];
}

export interface OverdueRow {
  taskId: string;
  title: string;
  assignee: UserRef | null;
  hoursOverdue: number;
}

export interface ReviewRow {
  taskId: string;
  title: string;
  assignees: UserRef[];
  submittedAt: string | null;
}

export interface DepartmentStat {
  id: string;
  name: string;
  active: number;
  completed: number;
  overdue: number;
}

export interface MemberStat {
  userId: string;
  name: string;
  open: number;
  inReview: number;
  overdue: number;
  completed: number;
}

export interface WeekPoint {
  weekStart: string;
  created: number;
  completed: number;
}

export interface ClubDashboard {
  clubId: string;
  clubName: string;
  members: number;
  activeTasks: number;
  completed: number;
  overdue: number;
  awaitingReview: number;
  upcomingEvents: number;
  byStatus: Record<TaskStatus, number>;
  overdueTasks: OverdueRow[];
  awaitingReviewTasks: ReviewRow[];
  departments: DepartmentStat[];
  workload: MemberStat[];
  weekly: WeekPoint[];
}

export interface ClubStat {
  id: string;
  name: string;
  members: number;
  activeTasks: number;
  completed: number;
  overdue: number;
}

export interface SuperAdminDashboard {
  clubs: number;
  users: number;
  activeTasks: number;
  completedTasks: number;
  overdueTasks: number;
  emailsSent: number;
  emailsPending: number;
  emailsFailed: number;
  clubStats: ClubStat[];
}
