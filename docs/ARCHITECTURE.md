# Architecture

## Shape

One Spring Boot application, one PostgreSQL database, one single-page frontend. No message broker, no cache, no separate worker. Scheduled jobs run inside the app and use database updates to avoid double work, which is enough for a club and easy to run for free.

```
Browser (React SPA)
   |  /api/*  (JWT bearer)
   v
Spring Boot
   |-- security     JWT filter, rate limit on auth, permission checks
   |-- feature packages: task, event, club, user, announcement, automation, dashboard, ...
   |-- schedulers   reminders (1 min), recurring tasks (10 min), email dispatch (10 s)
   v
PostgreSQL (schema owned by Flyway)        Local disk (attachments, UPLOAD_DIR)
```

## Backend

**Package by feature.** Each package holds its entities, repositories, DTOs, service and controller. Cross-feature calls go through services, not repositories.

**Authorization in two layers.** `@PreAuthorize` on controllers checks that the role has a permission. Services then check the *instance*: is this task in your club, are you assigned to it, is it in a state that allows this action. Permissions per role live in the database and are editable at runtime; defaults are in `DefaultRolePermissions`.

**Club isolation.** `AuthUser.scopeClub()` resolves which club a request works on. A normal user is always pinned to their own club and gets a 403 for any other; only a super admin may choose. Looking up another club's task returns 404, so ids do not leak existence.

**The task state machine** lives in `TaskService`. Each transition validates the current status, the caller's permission and the caller's relationship to the task, writes an audit entry, and notifies the right people. `actionsFor()` computes the set of allowed actions once, and the API returns it with every task so the UI renders buttons from the server's answer.

**Exactly-once scheduling.** A reminder is sent only if a single `UPDATE ... SET reminder_first_sent = true WHERE id = ? AND reminder_first_sent = false` changes one row. That makes reminders idempotent across restarts and across multiple instances, with no distributed lock. Recurring tasks use the same pattern with a `last_run_on` date.

**Email outbox.** Business code never talks to SMTP. It writes an outbox row in the same transaction as the change that caused it; a dispatcher sends pending rows with a retry limit. A mail provider outage delays email but cannot fail or slow a request, and a rolled-back change never sends a stray email.

**Files.** Attachments are stored on disk under `UPLOAD_DIR` with generated names. Original names are never used as paths, dangerous extensions are refused, downloads are forced to `Content-Disposition: attachment` with `nosniff`, and files are removed only after the database transaction commits.

**Audit log.** Append-only. Security-relevant and state-changing actions record who, what, when, and before/after values.

## Frontend

**State.** Server data lives in TanStack Query; there is no global store. Mutations return the updated object where the API does, and the page writes it straight into the cache, otherwise the relevant query keys are invalidated. Local component state is for form fields only.

**Auth.** The access token is kept in memory. The refresh token is in `localStorage`, so a reload can restore the session. That is a deliberate trade-off: it is exposed to any script injection, which is why the app renders no untrusted HTML and the CSP should stay strict in production. Moving the refresh token to an `HttpOnly` cookie is the natural hardening step and needs a small backend change. When a request gets a 401, one shared refresh call runs and the request is retried once.

**Permissions in the UI** hide navigation and buttons the person cannot use. That is convenience, not security; the server enforces everything.

**Club selection.** Pages that work inside a club read the active club from `ClubProvider`. For everyone except the super admin it is fixed; the super admin picks one from the header.

**Design system.** Small hand-written primitives in `components/ui.tsx` and one icon set in `components/Icon.tsx`, styled with Tailwind. It uses a chalk-and-marker palette (teal marker for actions, yellow highlighter for attention). The dashboard chart is plain SVG, so there is no charting dependency.

## Decisions worth knowing

| Decision | Why |
| --- | --- |
| Postgres as the only infrastructure | Clubs do not need Redis or a queue, and one database is easy to host for free. |
| Claim queries instead of a job framework | Simple, testable, correct with several instances. |
| Permissions in the database | A club can change who may approve work without a deploy. |
| Server-computed `actions` on tasks | One place decides what is allowed; the UI cannot drift from the API. |
| No component library or form library | Fewer dependencies to install and update; the primitives are small. |
| Same-origin API in Docker (nginx proxy) | No CORS configuration for the default setup. |

## Known gaps

- No password-reset-by-email flow. An admin resets a password and shares it privately.
- No email verification on sign-up; the join code is the gate.
- The refresh token is in `localStorage` (see above).
- Attachments are on local disk, so a multi-instance deployment needs shared storage (a volume or object store).
- No end-to-end browser tests.
