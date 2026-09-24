package com.clubflow.task;

import static com.clubflow.user.Permission.TASK_APPROVE;
import static com.clubflow.user.Permission.TASK_ASSIGN;
import static com.clubflow.user.Permission.TASK_DELETE;
import static com.clubflow.user.Permission.TASK_SUBMIT;
import static com.clubflow.user.Permission.TASK_UPDATE;
import static com.clubflow.user.Permission.TASK_UPDATE_ASSIGNED;
import static com.clubflow.user.Permission.TASK_VIEW_ALL;
import static com.clubflow.user.Permission.TASK_VIEW_ASSIGNED;

import com.clubflow.audit.AuditService;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.Department;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.common.ApiException;
import com.clubflow.common.Formats;
import com.clubflow.common.PageResponse;
import com.clubflow.config.AppProperties;
import com.clubflow.event.Event;
import com.clubflow.event.EventRepository;
import com.clubflow.notification.NotificationService;
import com.clubflow.notification.NotificationType;
import com.clubflow.security.AuthUser;
import com.clubflow.storage.StorageService;
import com.clubflow.task.TaskDtos.AttachmentDto;
import com.clubflow.task.TaskDtos.CommentDto;
import com.clubflow.task.TaskDtos.TaskActions;
import com.clubflow.task.TaskDtos.TaskDetail;
import com.clubflow.task.TaskDtos.TaskDto;
import com.clubflow.task.TaskDtos.TaskFilter;
import com.clubflow.task.TaskDtos.TaskRequest;
import com.clubflow.user.PermissionService;
import com.clubflow.user.User;
import com.clubflow.user.UserRef;
import com.clubflow.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * The task workflow.
 *
 * <pre>
 * TODO -> IN_PROGRESS -> SUBMITTED -> UNDER_REVIEW -> COMPLETED
 *                ^            |             |
 *                +-- reject --+-------------+        any active status -> CANCELLED -> (reopen) TODO
 * </pre>
 *
 * Every transition checks the caller's permission, records an audit row and notifies the people
 * affected. Notifications and emails are only queued here; sending happens on the outbox worker.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private static final int MAX_ATTACHMENTS_PER_TASK = 20;
    private static final Set<String> BLOCKED_EXTENSIONS =
            Set.of("exe", "bat", "cmd", "com", "scr", "msi", "ps1", "vbs", "jar", "dll");

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskAttachmentRepository attachments;
    private final UserRepository users;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final EventRepository events;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PermissionService permissions;
    private final StorageService storage;
    private final AppProperties props;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> list(AuthUser me, TaskFilter f, String sort, boolean desc, int page, int size) {
        UUID clubId = me.scopeClub(f.clubId());
        UUID assignee = f.assigneeId();
        if (Boolean.TRUE.equals(f.mine())) {
            assignee = me.id();
        }
        if (!me.can(TASK_VIEW_ALL)) {
            if (!me.can(TASK_VIEW_ASSIGNED)) {
                throw ApiException.forbidden("You do not have access to tasks.");
            }
            assignee = me.id();
        }
        Specification<Task> spec = TaskSpecs.matching(clubId, f.status(), f.priority(), f.departmentId(), f.eventId(),
                assignee, Boolean.TRUE.equals(f.overdue()), f.q(), Instant.now());

        Sort.Direction dir = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        String field = switch (sort == null ? "deadline" : sort.toLowerCase(Locale.ROOT)) {
            case "created", "createdat" -> "createdAt";
            case "updated", "updatedat" -> "updatedAt";
            default -> "deadline";
        };
        List<Sort.Order> order = new ArrayList<>();
        order.add(new Sort.Order(dir, field));
        if (!field.equals("createdAt")) {
            order.add(Sort.Order.desc("createdAt"));
        }
        Page<Task> result = tasks.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(order)));
        return PageResponse.of(result, toDtos(result.getContent()));
    }

    @Transactional(readOnly = true)
    public TaskDetail get(AuthUser me, UUID id) {
        return detail(me, requireViewable(me, id));
    }

    // ------------------------------------------------------------------ create / edit / assign

    public TaskDetail create(AuthUser me, TaskRequest req) {
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        requireFuture(req.deadline());

        List<UUID> assigneeIds = req.assigneeIds() == null ? List.of() : req.assigneeIds();
        if (!assigneeIds.isEmpty() && !me.can(TASK_ASSIGN)) {
            throw ApiException.forbidden("You can create tasks but not assign them.");
        }
        List<User> assignees = loadAssignees(clubId, assigneeIds);
        Department department = resolveDepartment(clubId, req.departmentId());
        Event event = resolveEvent(clubId, req.eventId());

        Task task = createInternal(club, users.getReferenceById(me.id()), me.name(), event, department,
                req.title(), req.description(), req.priority(), req.deadline(), assignees);
        return detail(me, task);
    }

    /**
     * Shared by the API and the automation jobs (templates, recurring tasks). Persists the task,
     * queues assignment notifications and writes the audit row. Does no permission checks itself.
     */
    public Task createInternal(Club club, User creator, String creatorName, Event event, Department department,
                               String title, String description, Priority priority, Instant deadline,
                               Collection<User> assignees) {
        Task task = new Task();
        task.setClub(club);
        task.setCreatedBy(creator);
        task.setEvent(event);
        task.setDepartment(department);
        task.setTitle(title.trim());
        task.setDescription(blankToNull(description));
        task.setPriority(priority == null ? Priority.MEDIUM : priority);
        task.setDeadline(deadline);
        for (User u : assignees) {
            task.getAssignees().add(new TaskAssignee(task, u));
        }
        tasks.save(task);
        audit.logAs(club.getId(), creator.getId(), creatorName, "TASK_CREATED", "TASK", task.getId(), null,
                task.getTitle());
        for (User u : assignees) {
            if (!u.getId().equals(creator.getId())) {
                notifyAssigned(task, u, creatorName);
            }
        }
        return task;
    }

    public TaskDetail update(AuthUser me, UUID id, TaskRequest req) {
        Task task = requireViewable(me, id);
        if (!me.can(TASK_UPDATE)) {
            throw ApiException.forbidden("You cannot edit tasks.");
        }
        if (!task.getStatus().isActive()) {
            throw ApiException.conflict("This task is " + task.getStatus().name().toLowerCase(Locale.ROOT)
                    + ". Reopen it before editing.");
        }
        UUID clubId = task.getClub().getId();

        boolean deadlineChanged = !task.getDeadline().equals(req.deadline());
        if (deadlineChanged) {
            requireFuture(req.deadline());
        }
        boolean otherChanged = !Objects.equals(task.getTitle(), req.title().trim())
                || !Objects.equals(task.getDescription(), blankToNull(req.description()))
                || task.getPriority() != req.priority()
                || !Objects.equals(idOf(task.getDepartment()), req.departmentId())
                || !Objects.equals(task.getEvent() == null ? null : task.getEvent().getId(), req.eventId());

        Instant oldDeadline = task.getDeadline();
        task.setTitle(req.title().trim());
        task.setDescription(blankToNull(req.description()));
        task.setPriority(req.priority());
        task.setDepartment(resolveDepartment(clubId, req.departmentId()));
        task.setEvent(resolveEvent(clubId, req.eventId()));
        task.setDeadline(req.deadline());
        task.setUpdatedAt(Instant.now());

        if (deadlineChanged) {
            task.resetReminders();
            audit.log(me, clubId, "TASK_DEADLINE_CHANGED", "TASK", id, oldDeadline, req.deadline());
        }
        if (otherChanged) {
            audit.log(me, clubId, "TASK_UPDATED", "TASK", id, null, task.getTitle());
        }

        if (req.assigneeIds() != null) {
            if (!me.can(TASK_ASSIGN)) {
                throw ApiException.forbidden("You do not have permission to change assignees.");
            }
            syncAssignees(me, task, req.assigneeIds());
        }

        List<User> people = task.assigneeUsers().stream().filter(u -> !u.getId().equals(me.id())).toList();
        if (deadlineChanged) {
            String zone = props.timezone();
            notifications.notifyAll(people, NotificationType.TASK_DEADLINE_CHANGED,
                    "Deadline changed: " + task.getTitle(),
                    me.name() + " moved the deadline from " + Formats.dateTime(oldDeadline, zone) + " to "
                            + Formats.dateTime(task.getDeadline(), zone) + ".",
                    link(task));
        } else if (otherChanged) {
            notifications.notifyAll(people, NotificationType.TASK_UPDATED, "Task updated: " + task.getTitle(),
                    me.name() + " updated the details of this task.", link(task));
        }
        return detail(me, task);
    }

    public TaskDetail assign(AuthUser me, UUID id, List<UUID> userIds) {
        Task task = requireViewable(me, id);
        if (!me.can(TASK_ASSIGN)) {
            throw ApiException.forbidden("You cannot assign tasks.");
        }
        if (!task.getStatus().isActive()) {
            throw ApiException.conflict("Finished or cancelled tasks cannot be reassigned.");
        }
        syncAssignees(me, task, userIds);
        task.setUpdatedAt(Instant.now());
        return detail(me, task);
    }

    public void delete(AuthUser me, UUID id) {
        Task task = requireViewable(me, id);
        if (!me.can(TASK_DELETE)) {
            throw ApiException.forbidden("You cannot delete tasks.");
        }
        List<TaskAttachment> files = attachments.findByTaskIdOrderByCreatedAtAsc(id);
        List<String> keys = files.stream().map(TaskAttachment::getStorageKey).toList();
        attachments.deleteAll(files);
        tasks.delete(task);
        audit.log(me, task.getClub().getId(), "TASK_DELETED", "TASK", id, task.getTitle(), null);
        deleteFilesAfterCommit(keys);
    }

    // ------------------------------------------------------------------ workflow transitions

    /** Assignee picks the task up: TODO -> IN_PROGRESS. */
    public TaskDetail start(AuthUser me, UUID id) {
        Task task = requireViewable(me, id);
        if (!canStart(me, task)) {
            throw transitionError(me, task, "start");
        }
        changeStatus(me, task, TaskStatus.IN_PROGRESS);
        return detail(me, task);
    }

    /** Assignee hands the work in for review: TODO / IN_PROGRESS -> SUBMITTED. */
    public TaskDetail submit(AuthUser me, UUID id, String note) {
        Task task = requireViewable(me, id);
        if (!canSubmit(me, task)) {
            throw transitionError(me, task, "submit");
        }
        changeStatus(me, task, TaskStatus.SUBMITTED);
        task.setSubmittedAt(Instant.now());
        task.setReviewNote(null);
        addComment(task, me, note);

        Set<User> reviewers = new LinkedHashSet<>();
        User creator = task.getCreatedBy();
        if (creator.isActive() && !creator.getId().equals(me.id())) {
            reviewers.add(creator);
        }
        if (!permissions.permissionsFor(creator.getRole()).contains(TASK_APPROVE)) {
            users.findByClubIdAndActiveTrue(task.getClub().getId()).stream()
                    .filter(u -> !u.getId().equals(me.id()))
                    .filter(u -> permissions.permissionsFor(u.getRole()).contains(TASK_APPROVE))
                    .forEach(reviewers::add);
        }
        notifications.notifyAll(reviewers, NotificationType.TASK_SUBMITTED, "Submitted for review: " + task.getTitle(),
                me.name() + " submitted this task for review.", link(task));
        return detail(me, task);
    }

    /** Reviewer opens the submission: SUBMITTED -> UNDER_REVIEW. */
    public TaskDetail startReview(AuthUser me, UUID id) {
        Task task = requireViewable(me, id);
        if (!me.can(TASK_APPROVE) || task.getStatus() != TaskStatus.SUBMITTED) {
            throw transitionError(me, task, "start reviewing");
        }
        changeStatus(me, task, TaskStatus.UNDER_REVIEW);
        notifyAssignees(task, me, NotificationType.TASK_UPDATED, "Under review: " + task.getTitle(),
                me.name() + " has started reviewing your submission.");
        return detail(me, task);
    }

    /** Reviewer accepts the work: SUBMITTED / UNDER_REVIEW -> COMPLETED. */
    public TaskDetail approve(AuthUser me, UUID id, String note) {
        Task task = requireViewable(me, id);
        requireReviewable(me, task, "approve");
        changeStatus(me, task, TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setReviewNote(null);
        addComment(task, me, note == null || note.isBlank() ? null : "Approved: " + note.trim());
        notifyAssignees(task, me, NotificationType.TASK_APPROVED, "Approved: " + task.getTitle(),
                me.name() + " approved your work." + (note == null || note.isBlank() ? "" : " \"" + note.trim() + "\""));
        return detail(me, task);
    }

    /** Reviewer sends the work back with a reason: SUBMITTED / UNDER_REVIEW -> IN_PROGRESS. */
    public TaskDetail reject(AuthUser me, UUID id, String reason) {
        Task task = requireViewable(me, id);
        requireReviewable(me, task, "request changes on");
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Tell the assignee what needs to change.");
        }
        changeStatus(me, task, TaskStatus.IN_PROGRESS);
        task.setSubmittedAt(null);
        task.setReviewNote(reason.trim());
        addComment(task, me, "Changes requested: " + reason.trim());
        notifyAssignees(task, me, NotificationType.TASK_REJECTED, "Changes requested: " + task.getTitle(),
                me.name() + " asked for changes: \"" + reason.trim() + "\"");
        return detail(me, task);
    }

    public TaskDetail cancel(AuthUser me, UUID id, String reason) {
        Task task = requireViewable(me, id);
        if (!me.can(TASK_UPDATE) || !task.getStatus().isActive()) {
            throw transitionError(me, task, "cancel");
        }
        TaskStatus before = task.getStatus();
        task.setStatus(TaskStatus.CANCELLED);
        task.setUpdatedAt(Instant.now());
        audit.log(me, task.getClub().getId(), "TASK_CANCELLED", "TASK", id, before, blankToNull(reason));
        notifyAssignees(task, me, NotificationType.TASK_CANCELLED, "Cancelled: " + task.getTitle(),
                me.name() + " cancelled this task." + (reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim()));
        return detail(me, task);
    }

    /** Brings a finished or cancelled task back to TODO. Reminders start over for the current deadline. */
    public TaskDetail reopen(AuthUser me, UUID id) {
        Task task = requireViewable(me, id);
        TaskStatus s = task.getStatus();
        if (!me.can(TASK_UPDATE) || (s != TaskStatus.COMPLETED && s != TaskStatus.CANCELLED)) {
            throw ApiException.conflict("Only completed or cancelled tasks can be reopened.");
        }
        task.setCompletedAt(null);
        task.setSubmittedAt(null);
        task.setReviewNote(null);
        task.resetReminders();
        changeStatus(me, task, TaskStatus.TODO);
        notifyAssignees(task, me, NotificationType.TASK_UPDATED, "Reopened: " + task.getTitle(),
                me.name() + " reopened this task.");
        return detail(me, task);
    }

    // ------------------------------------------------------------------ comments

    public TaskDetail comment(AuthUser me, UUID id, String body) {
        Task task = requireViewable(me, id);
        addComment(task, me, body);
        Set<User> audience = new LinkedHashSet<>(task.assigneeUsers());
        audience.add(task.getCreatedBy());
        audience.removeIf(u -> u.getId().equals(me.id()));
        notifications.notifyAll(audience, NotificationType.TASK_COMMENT, "New comment on " + task.getTitle(),
                me.name() + ": " + abbreviate(body, 240), link(task));
        return detail(me, task);
    }

    // ------------------------------------------------------------------ attachments

    public record Download(Resource resource, String fileName, String contentType, long sizeBytes) {}

    public TaskDetail upload(AuthUser me, UUID id, MultipartFile file) {
        Task task = requireViewable(me, id);
        if (!canAttach(me, task)) {
            throw ApiException.forbidden("You cannot add files to this task right now.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload.");
        }
        if (attachments.findByTaskIdOrderByCreatedAtAsc(id).size() >= MAX_ATTACHMENTS_PER_TASK) {
            throw ApiException.badRequest("A task can hold at most " + MAX_ATTACHMENTS_PER_TASK + " files.");
        }
        String name = safeFileName(file.getOriginalFilename());
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("Files of type ." + ext + " cannot be uploaded.");
        }

        String key = storage.store(file);
        deleteFileIfRolledBack(key);

        TaskAttachment a = new TaskAttachment();
        a.setTask(task);
        a.setUploadedBy(users.getReferenceById(me.id()));
        a.setFileName(name);
        a.setStorageKey(key);
        a.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        a.setSizeBytes(file.getSize());
        attachments.save(a);
        task.setUpdatedAt(Instant.now());
        audit.log(me, task.getClub().getId(), "TASK_FILE_ADDED", "TASK", id, null, name);
        return detail(me, task);
    }

    @Transactional(readOnly = true)
    public Download download(AuthUser me, UUID taskId, UUID attachmentId) {
        requireViewable(me, taskId);
        TaskAttachment a = requireAttachment(taskId, attachmentId);
        return new Download(storage.load(a.getStorageKey()), a.getFileName(), a.getContentType(), a.getSizeBytes());
    }

    public TaskDetail deleteAttachment(AuthUser me, UUID taskId, UUID attachmentId) {
        Task task = requireViewable(me, taskId);
        TaskAttachment a = requireAttachment(taskId, attachmentId);
        boolean mine = a.getUploadedBy().getId().equals(me.id());
        boolean editable = task.getStatus().isOpen() || me.can(TASK_UPDATE);
        if (!(mine && editable) && !me.can(TASK_UPDATE)) {
            throw ApiException.forbidden("You can only remove your own files while the task is open.");
        }
        attachments.delete(a);
        deleteFilesAfterCommit(List.of(a.getStorageKey()));
        audit.log(me, task.getClub().getId(), "TASK_FILE_REMOVED", "TASK", taskId, a.getFileName(), null);
        return detail(me, task);
    }

    // ------------------------------------------------------------------ permission rules
    // These are used both to enforce transitions and to tell the UI which buttons to show.

    private boolean isAssignee(AuthUser me, Task t) {
        return t.hasAssignee(me.id());
    }

    private boolean canStart(AuthUser me, Task t) {
        return isAssignee(me, t) && me.can(TASK_UPDATE_ASSIGNED) && t.getStatus() == TaskStatus.TODO;
    }

    private boolean canSubmit(AuthUser me, Task t) {
        return isAssignee(me, t) && me.can(TASK_SUBMIT) && t.getStatus().isOpen();
    }

    private boolean canReview(AuthUser me, Task t) {
        return me.can(TASK_APPROVE) && TaskStatus.AWAITING_REVIEW.contains(t.getStatus());
    }

    private boolean canAttach(AuthUser me, Task t) {
        boolean asAssignee = isAssignee(me, t) && me.can(TASK_UPDATE_ASSIGNED) && t.getStatus().isOpen();
        boolean asManager = me.can(TASK_UPDATE) && t.getStatus().isActive();
        return asAssignee || asManager;
    }

    private TaskActions actionsFor(AuthUser me, Task t) {
        TaskStatus s = t.getStatus();
        return new TaskActions(
                me.can(TASK_UPDATE) && s.isActive(),
                me.can(TASK_ASSIGN) && s.isActive(),
                canStart(me, t),
                canSubmit(me, t),
                me.can(TASK_APPROVE) && s == TaskStatus.SUBMITTED,
                canReview(me, t),
                me.can(TASK_UPDATE) && s.isActive(),
                me.can(TASK_UPDATE) && (s == TaskStatus.COMPLETED || s == TaskStatus.CANCELLED),
                me.can(TASK_DELETE),
                canAttach(me, t));
    }

    private void requireReviewable(AuthUser me, Task task, String verb) {
        if (!canReview(me, task)) {
            throw ApiException.conflict("You can only " + verb + " tasks that have been submitted for review.");
        }
    }

    private ApiException transitionError(AuthUser me, Task task, String verb) {
        if (!me.can(TASK_UPDATE_ASSIGNED) && !me.can(TASK_UPDATE) && !me.can(TASK_APPROVE)) {
            return ApiException.forbidden("You do not have permission to " + verb + " this task.");
        }
        return ApiException.conflict("This task cannot be moved that way from "
                + task.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
    }

    // ------------------------------------------------------------------ helpers

    /** Loads the task and hides it (404) from anyone outside the club or, for members, not assigned to it. */
    private Task requireViewable(AuthUser me, UUID id) {
        Task task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Task not found."));
        if (!me.sameClub(task.getClub().getId())) {
            throw ApiException.notFound("Task not found.");
        }
        if (!me.can(TASK_VIEW_ALL)) {
            if (!me.can(TASK_VIEW_ASSIGNED) || !task.hasAssignee(me.id())) {
                throw ApiException.notFound("Task not found.");
            }
        }
        return task;
    }

    private void changeStatus(AuthUser me, Task task, TaskStatus to) {
        TaskStatus from = task.getStatus();
        task.setStatus(to);
        task.setUpdatedAt(Instant.now());
        audit.log(me, task.getClub().getId(), "TASK_STATUS_CHANGED", "TASK", task.getId(), from, to);
    }

    private void addComment(Task task, AuthUser author, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        TaskComment c = new TaskComment();
        c.setTask(task);
        c.setAuthor(users.getReferenceById(author.id()));
        c.setBody(body.trim());
        comments.save(c);
    }

    private void syncAssignees(AuthUser me, Task task, List<UUID> requested) {
        UUID clubId = task.getClub().getId();
        List<User> wanted = loadAssignees(clubId, requested);
        Set<UUID> wantedIds = wanted.stream().map(User::getId).collect(Collectors.toSet());
        Set<UUID> currentIds = task.getAssignees().stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());

        List<User> removed = task.getAssignees().stream().map(TaskAssignee::getUser)
                .filter(u -> !wantedIds.contains(u.getId())).toList();
        task.getAssignees().removeIf(a -> !wantedIds.contains(a.getUser().getId()));
        List<User> added = wanted.stream().filter(u -> !currentIds.contains(u.getId())).toList();
        for (User u : added) {
            task.getAssignees().add(new TaskAssignee(task, u));
        }
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        audit.log(me, clubId, "TASK_ASSIGNEES_CHANGED", "TASK", task.getId(),
                removed.stream().map(User::getName).toList(), added.stream().map(User::getName).toList());
        for (User u : added) {
            if (!u.getId().equals(me.id())) {
                notifyAssigned(task, u, me.name());
            }
        }
        for (User u : removed) {
            if (!u.getId().equals(me.id())) {
                notifications.notify(u, NotificationType.TASK_UPDATED, "Removed from: " + task.getTitle(),
                        me.name() + " removed you from this task.", null);
            }
        }
    }

    private void notifyAssigned(Task task, User to, String byName) {
        notifications.notify(to, NotificationType.TASK_ASSIGNED, "New task: " + task.getTitle(),
                byName + " assigned you \"" + task.getTitle() + "\". Priority " + task.getPriority().name().toLowerCase(Locale.ROOT)
                        + ", due " + Formats.dateTime(task.getDeadline(), props.timezone()) + ".",
                link(task));
    }

    private void notifyAssignees(Task task, AuthUser actor, NotificationType type, String title, String message) {
        List<User> people = task.assigneeUsers().stream().filter(u -> !u.getId().equals(actor.id())).toList();
        notifications.notifyAll(people, type, title, message, link(task));
    }

    private List<User> loadAssignees(UUID clubId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        List<User> found = users.findAllById(distinct);
        boolean ok = found.size() == distinct.size() && found.stream()
                .allMatch(u -> u.isActive() && u.getClub() != null && u.getClub().getId().equals(clubId));
        if (!ok) {
            throw ApiException.badRequest("Everyone assigned must be an active member of this club.");
        }
        return found;
    }

    private Department resolveDepartment(UUID clubId, UUID id) {
        if (id == null) {
            return null;
        }
        Department d = departments.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown department."));
        if (!d.getClub().getId().equals(clubId)) {
            throw ApiException.badRequest("That department belongs to another club.");
        }
        return d;
    }

    private Event resolveEvent(UUID clubId, UUID id) {
        if (id == null) {
            return null;
        }
        Event e = events.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown event."));
        if (!e.getClub().getId().equals(clubId)) {
            throw ApiException.badRequest("That event belongs to another club.");
        }
        return e;
    }

    private TaskAttachment requireAttachment(UUID taskId, UUID attachmentId) {
        TaskAttachment a = attachments.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("File not found."));
        if (!a.getTask().getId().equals(taskId)) {
            throw ApiException.notFound("File not found.");
        }
        return a;
    }

    private void requireFuture(Instant deadline) {
        if (!deadline.isAfter(Instant.now())) {
            throw ApiException.badRequest("The deadline must be in the future.");
        }
    }

    private static UUID idOf(Department d) {
        return d == null ? null : d.getId();
    }

    private static String link(Task t) {
        return "/tasks/" + t.getId();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String abbreviate(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** Strips any path, control characters and over-long names from an uploaded file name. */
    private static String safeFileName(String original) {
        String name = original == null ? "file" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "file";
        }
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name;
    }

    private void deleteFilesAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(storage::delete);
                }
            });
        } else {
            keys.forEach(storage::delete);
        }
    }

    private void deleteFileIfRolledBack(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.delete(key);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ DTO mapping

    private TaskDetail detail(AuthUser me, Task task) {
        List<CommentDto> commentDtos = comments.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(c -> new CommentDto(c.getId(), c.getBody(), UserRef.of(c.getAuthor()), c.getCreatedAt()))
                .toList();
        List<AttachmentDto> fileDtos = attachments.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(a -> new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                        UserRef.of(a.getUploadedBy()), a.getCreatedAt()))
                .toList();
        TaskDto dto = toDtos(List.of(task)).get(0);
        return new TaskDetail(dto, commentDtos, fileDtos, actionsFor(me, task));
    }

    public List<TaskDto> toDtos(List<Task> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Task::getId).toList();
        Map<UUID, Long> commentCounts = counts(comments.countByTaskIds(ids));
        Map<UUID, Long> fileCounts = counts(attachments.countByTaskIds(ids));
        Instant now = Instant.now();
        return rows.stream().map(t -> new TaskDto(
                t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(), t.getDeadline(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getSubmittedAt(), t.getCompletedAt(), t.getReviewNote(),
                t.isOverdue(now), t.getClub().getId(),
                t.getDepartment() == null ? null : t.getDepartment().getId(),
                t.getDepartment() == null ? null : t.getDepartment().getName(),
                t.getEvent() == null ? null : t.getEvent().getId(),
                t.getEvent() == null ? null : t.getEvent().getTitle(),
                UserRef.of(t.getCreatedBy()),
                t.getAssignees().stream().map(a -> UserRef.of(a.getUser())).toList(),
                commentCounts.getOrDefault(t.getId(), 0L), fileCounts.getOrDefault(t.getId(), 0L))).toList();
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }
}
