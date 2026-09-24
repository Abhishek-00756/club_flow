-- ClubFlow schema. Hibernate runs with ddl-auto=validate, so keep this in sync with the entities.

create table clubs (
    id          uuid primary key,
    name        varchar(120) not null unique,
    description text,
    join_code   varchar(20)  not null unique,
    active      boolean      not null default true,
    created_at  timestamptz  not null
);

create table departments (
    id      uuid primary key,
    club_id uuid        not null references clubs (id) on delete cascade,
    name    varchar(80) not null,
    unique (club_id, name)
);

create table users (
    id                  uuid primary key,
    club_id             uuid references clubs (id),
    department_id       uuid references departments (id) on delete set null,
    name                varchar(120) not null,
    email               varchar(180) not null unique,
    password_hash       varchar(100) not null,
    role                varchar(30)  not null,
    active              boolean      not null default true,
    email_notifications boolean      not null default true,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    last_login_at       timestamptz
);
create index idx_users_club on users (club_id);

create table refresh_tokens (
    id          uuid primary key,
    user_id     uuid         not null references users (id) on delete cascade,
    token_hash  varchar(64)  not null unique,
    created_at  timestamptz  not null,
    expires_at  timestamptz  not null,
    revoked     boolean      not null default false,
    user_agent  varchar(255),
    ip_address  varchar(64)
);
create index idx_refresh_user on refresh_tokens (user_id);

create table role_permissions (
    id         uuid primary key,
    role       varchar(30) not null,
    permission varchar(50) not null,
    unique (role, permission)
);

create table events (
    id          uuid primary key,
    club_id     uuid         not null references clubs (id),
    title       varchar(200) not null,
    description text,
    location    varchar(200),
    starts_at   timestamptz  not null,
    ends_at     timestamptz  not null,
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null
);
create index idx_events_club_start on events (club_id, starts_at);

create table event_attendees (
    id            uuid primary key,
    event_id      uuid        not null references events (id) on delete cascade,
    user_id       uuid        not null references users (id) on delete cascade,
    participation varchar(20) not null,
    attended      boolean     not null default false,
    checked_in_at timestamptz,
    unique (event_id, user_id)
);

create table tasks (
    id                    uuid primary key,
    club_id               uuid         not null references clubs (id),
    event_id              uuid references events (id) on delete set null,
    department_id         uuid references departments (id) on delete set null,
    title                 varchar(200) not null,
    description           text,
    priority              varchar(20)  not null,
    status                varchar(20)  not null,
    deadline              timestamptz  not null,
    created_by            uuid         not null references users (id),
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    submitted_at          timestamptz,
    completed_at          timestamptz,
    review_note           text,
    reminder_first_sent   boolean      not null default false,
    reminder_second_sent  boolean      not null default false,
    overdue_notified      boolean      not null default false
);
create index idx_tasks_club_status on tasks (club_id, status);
create index idx_tasks_deadline on tasks (deadline);
create index idx_tasks_event on tasks (event_id);

create table task_assignees (
    id          uuid primary key,
    task_id     uuid        not null references tasks (id) on delete cascade,
    user_id     uuid        not null references users (id),
    assigned_at timestamptz not null,
    unique (task_id, user_id)
);
create index idx_task_assignees_user on task_assignees (user_id);

create table task_comments (
    id         uuid primary key,
    task_id    uuid        not null references tasks (id) on delete cascade,
    author_id  uuid        not null references users (id),
    body       text        not null,
    created_at timestamptz not null
);
create index idx_task_comments_task on task_comments (task_id);

create table task_attachments (
    id           uuid primary key,
    task_id      uuid         not null references tasks (id) on delete cascade,
    uploaded_by  uuid         not null references users (id),
    file_name    varchar(255) not null,
    storage_key  varchar(255) not null,
    content_type varchar(120),
    size_bytes   bigint       not null,
    created_at   timestamptz  not null
);
create index idx_task_attachments_task on task_attachments (task_id);

create table announcements (
    id         uuid primary key,
    club_id    uuid         not null references clubs (id),
    author_id  uuid         not null references users (id),
    title      varchar(200) not null,
    body       text         not null,
    created_at timestamptz  not null
);
create index idx_announcements_club on announcements (club_id, created_at desc);

create table notifications (
    id         uuid primary key,
    user_id    uuid         not null references users (id) on delete cascade,
    type       varchar(40)  not null,
    title      varchar(200) not null,
    message    text,
    link       varchar(255),
    is_read    boolean      not null default false,
    created_at timestamptz  not null
);
create index idx_notifications_user on notifications (user_id, is_read, created_at desc);

-- Email outbox: rows are written in the same transaction as the business change,
-- then delivered by a background worker (never from the request thread).
create table email_outbox (
    id         uuid primary key,
    to_email   varchar(180) not null,
    subject    varchar(250) not null,
    html_body  text         not null,
    status     varchar(20)  not null,
    attempts   int          not null default 0,
    last_error text,
    created_at timestamptz  not null,
    next_attempt_at timestamptz not null,
    sent_at    timestamptz
);
create index idx_email_outbox_status on email_outbox (status, next_attempt_at);

create table audit_logs (
    id          uuid primary key,
    club_id     uuid,
    user_id     uuid,
    user_name   varchar(120),
    action      varchar(60)  not null,
    entity_type varchar(40)  not null,
    entity_id   varchar(64),
    old_value   text,
    new_value   text,
    ip_address  varchar(64),
    created_at  timestamptz  not null
);
create index idx_audit_club_time on audit_logs (club_id, created_at desc);

create table task_templates (
    id          uuid primary key,
    club_id     uuid         not null references clubs (id) on delete cascade,
    name        varchar(120) not null,
    description text
);

create table task_template_items (
    id                uuid primary key,
    template_id       uuid         not null references task_templates (id) on delete cascade,
    title             varchar(200) not null,
    description       text,
    priority          varchar(20)  not null,
    department_id     uuid references departments (id) on delete set null,
    days_before_event int          not null default 7,
    position          int          not null default 0
);

create table recurring_tasks (
    id            uuid primary key,
    club_id       uuid         not null references clubs (id) on delete cascade,
    department_id uuid references departments (id) on delete set null,
    title         varchar(200) not null,
    description   text,
    priority      varchar(20)  not null,
    frequency     varchar(20)  not null,
    day_of_week   int,
    day_of_month  int,
    due_in_days   int          not null default 3,
    due_time      time         not null,
    active        boolean      not null default true,
    last_run_on   date,
    created_by    uuid         not null references users (id)
);

create table recurring_task_assignees (
    recurring_task_id uuid not null references recurring_tasks (id) on delete cascade,
    user_id           uuid not null references users (id) on delete cascade,
    primary key (recurring_task_id, user_id)
);
