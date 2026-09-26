# ClubFlow

Task assignment, review and reminders for a college club. A secretary creates and assigns work, members submit it, a reviewer approves it or sends it back with a reason, and the system does the chasing: reminders before a deadline, an alert when one is missed, and recurring tasks that create themselves.

## Features

- Task creation, assignment, submission and review workflow
- Role-based permissions for members, secretaries, admins and super admins
- Deadline reminders and overdue notifications
- Recurring tasks and event-based task templates
- JWT-based authentication
- Club-level data isolation
- Email notifications with retry support
- Dashboard and reports for club management
- Docker-based setup for quick local development


- **Backend:** Spring Boot 3.3, Java 21, PostgreSQL, Flyway, JWT auth
- **Frontend:** React 18, TypeScript, Tailwind CSS, TanStack Query, Vite

## Start it

You need Docker.

```bash
docker compose up --build
```

| What | Where |
| --- | --- |
| The app | http://localhost:5173 |
| API and Swagger UI | http://localhost:8080/swagger-ui.html |
| Every email the app sends (nothing leaves your machine) | http://localhost:8025 |

The first start creates a super admin and a demo club so there is something to look at:

| Who | Email | Password |
| --- | --- | --- |
| Super admin | `admin@clubflow.local` | `ChangeMe123!` |
| Admin | `admin@astroclub.example` | `Password123!` |
| Secretary | `secretary@astroclub.example` | `Password123!` |
| Joint secretary | `jointsecretary@astroclub.example` | `Password123!` |
| Members | `rahul@`, `aman@`, `sneha@`, `karan@`, `ananya@`, `vikram@astroclub.example` | `Password123!` |

The demo club's join code is `ASTRO2026`. To start empty, set `SEED_DEMO_DATA` to `"false"` in `docker-compose.yml`.

### Without Docker

```bash
# 1. PostgreSQL 16 with a database and user both called clubflow (password clubflow), and optionally
#    Mailpit (or any SMTP catcher) on localhost:1025.

# 2. Backend
cd backend
SEED_DEMO_DATA=true mvn spring-boot:run

# 3. Frontend, in another terminal
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to :8080
```

## How work flows

```
To do  ->  In progress  ->  Submitted  ->  Under review  ->  Completed
                ^               |               |
                +---------------+---------------+      (reviewer sends it back with a required reason)

Cancelled can be reached from any unfinished state, and reopened later.
```

- The server decides which buttons a person sees (`actions` on every task), so the UI never offers something the API would refuse.
- Only work that is still **To do** or **In progress** can be overdue or get reminders. Submitted work is waiting on the reviewer, not the assignee.
- Moving a deadline re-arms its reminders.

## Roles

Roles map to permissions, and the mapping is editable under **Roles and permissions** (super admin only). Defaults:

| | Member | Joint secretary | Secretary | Admin | Super admin |
| --- | :-: | :-: | :-: | :-: | :-: |
| See own tasks, start and submit them | yes | yes | yes | yes | yes |
| See every task in the club | | yes | yes | yes | yes |
| Create and assign tasks | | yes | yes | yes | yes |
| Review, approve, send back | | | yes | yes | yes |
| Create and edit events | | create | yes | yes | yes |
| Post announcements, manage templates and repeats | | | yes | yes | yes |
| Club dashboard and reports | | | yes | yes | yes |
| Add, edit and remove members, departments, audit log | | | | yes | yes |
| Create clubs, edit role permissions, email delivery | | | | | yes |

Everyone is confined to their own club. Only the super admin can switch between clubs.

## Automation

- **Reminders.** A scan runs every minute. Open tasks get a reminder 24 hours before the deadline, another 3 hours before, and an overdue alert when it is missed (the assignees and whoever created the task are told). Each reminder is claimed with a single database update, so it goes out exactly once even if you run several backend instances.
- **Repeating tasks.** Daily, weekly or monthly. A monthly task set for the 31st runs on the last day of shorter months. Also claimed atomically per day.
- **Event templates.** A checklist of tasks with "days before the event" offsets. Applying one to an event creates real tasks with the deadlines worked out. A task that would start life already overdue is pulled forward to just after now.
- **Email.** Notifications write to an outbox table and a dispatcher sends them with retries, so a mail outage never blocks a request. The outbox is visible under **Email delivery**.

## Configuration

Everything is an environment variable with a development default. `.env.example` lists the ones you should set for a real deployment.

| Variable | Default | Notes |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local Postgres | |
| `JWT_SECRET` | dev-only value | **Change it.** At least 32 characters. |
| `JWT_ACCESS_MINUTES`, `JWT_REFRESH_DAYS` | 15, 14 | |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS only matters if the frontend and API are on different domains. |
| `APP_TIMEZONE` | `Asia/Kolkata` | Used when writing deadlines in emails. |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` | Mailpit on localhost | See `.env.example` for a Resend example. |
| `SEED_ENABLED`, `SUPERADMIN_NAME`, `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD` | on, `admin@clubflow.local`, `ChangeMe123!` | The super admin is created only if it does not exist. The app warns at startup while the default password is in use. |
| `SEED_DEMO_DATA` | `false` | Demo club, only into an empty database. |
| `UPLOAD_DIR` | `./uploads` | Attachments. Mount a volume here. |
| `AUTH_RATE_LIMIT_PER_MINUTE` | 20 | Per IP, on login and register. |
| `VITE_API_URL` (frontend, build time) | empty | Leave empty for same-origin. Set it if the API is on another domain. |

## Layout

```
backend/    Spring Boot app: one package per feature (task, event, user, club, automation, ...)
frontend/   React app: pages/, components/, lib/ (api client, auth, types)
docs/       Architecture notes
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit and the decisions behind them.

## Tests

```bash
cd backend && mvn verify
```

Unit tests cover the status rules, permission defaults, club scoping, recurring-schedule maths and the reminder logic. One integration test walks a task through create, assign, start, submit, reject, resubmit and approve against a real PostgreSQL through Testcontainers, and checks role enforcement and cross-club isolation. It is skipped automatically when Docker is not running.

## Status of this code

Read this before you trust it.

- The backend was written and syntax-checked, but **not compiled against its real dependencies**, and the tests **have not been run**. The environment it was built in could not reach Maven Central or npm.
- The frontend was type-checked against hand-written stand-ins for React, React Router and TanStack Query, **not the real packages**, and was never built.
- Expect a handful of small fixes on the first `mvn verify` and `npm run build`. The first real run is the real test.
- No `package-lock.json` is committed. After your first `npm install`, commit it and switch the frontend CI step and Dockerfile from `npm install` to `npm ci`.
