package com.clubflow.automation;

import static com.clubflow.user.Permission.TASK_ASSIGN;

import com.clubflow.audit.AuditService;
import com.clubflow.automation.TemplateDtos.ApplyRequest;
import com.clubflow.automation.TemplateDtos.ApplyResult;
import com.clubflow.automation.TemplateDtos.ItemDto;
import com.clubflow.automation.TemplateDtos.ItemRequest;
import com.clubflow.automation.TemplateDtos.TemplateDto;
import com.clubflow.automation.TemplateDtos.TemplateRequest;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.Department;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.common.ApiException;
import com.clubflow.event.Event;
import com.clubflow.event.EventRepository;
import com.clubflow.security.AuthUser;
import com.clubflow.task.Task;
import com.clubflow.task.TaskService;
import com.clubflow.user.User;
import com.clubflow.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task templates: define a checklist once, generate the real tasks for each new event. */
@Service
@RequiredArgsConstructor
@Transactional
public class TemplateService {
    private final TaskTemplateRepository templates;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final EventRepository events;
    private final UserRepository users;
    private final TaskService taskService;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<TemplateDto> list(AuthUser me, UUID requestedClubId) {
        UUID clubId = me.scopeClub(requestedClubId);
        List<TaskTemplate> rows = clubId == null ? templates.findAllByOrderByNameAsc()
                : templates.findByClubIdOrderByNameAsc(clubId);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TemplateDto get(AuthUser me, UUID id) {
        return toDto(requireAccessible(me, id));
    }

    public TemplateDto create(AuthUser me, TemplateRequest req) {
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        TaskTemplate t = new TaskTemplate();
        t.setClub(club);
        apply(t, req, clubId);
        templates.save(t);
        audit.log(me, clubId, "TEMPLATE_CREATED", "TEMPLATE", t.getId(), null, t.getName());
        return toDto(t);
    }

    public TemplateDto update(AuthUser me, UUID id, TemplateRequest req) {
        TaskTemplate t = requireAccessible(me, id);
        String before = t.getName();
        apply(t, req, t.getClub().getId());
        audit.log(me, t.getClub().getId(), "TEMPLATE_UPDATED", "TEMPLATE", id, before, t.getName());
        return toDto(t);
    }

    public void delete(AuthUser me, UUID id) {
        TaskTemplate t = requireAccessible(me, id);
        templates.delete(t);
        audit.log(me, t.getClub().getId(), "TEMPLATE_DELETED", "TEMPLATE", id, t.getName(), null);
    }

    /**
     * Creates one task per template item, due a set number of days before the event starts.
     * Items whose due date has already slipped past are pulled forward so nothing is created overdue.
     */
    public ApplyResult applyToEvent(AuthUser me, UUID templateId, ApplyRequest req) {
        TaskTemplate template = requireAccessible(me, templateId);
        Event event = events.findById(req.eventId()).orElseThrow(() -> ApiException.notFound("Event not found."));
        if (!event.getClub().getId().equals(template.getClub().getId())) {
            throw ApiException.badRequest("The template and the event belong to different clubs.");
        }
        Instant now = Instant.now();
        if (!event.getStartsAt().isAfter(now.plus(Duration.ofHours(1)))) {
            throw ApiException.badRequest("This event starts too soon to plan tasks for it.");
        }
        if (req.assignToDepartments() && !me.can(TASK_ASSIGN)) {
            throw ApiException.forbidden("You do not have permission to assign tasks.");
        }

        User creator = users.getReferenceById(me.id());
        List<UUID> created = new ArrayList<>();
        for (TaskTemplateItem item : template.getItems()) {
            Instant deadline = event.getStartsAt().minus(Duration.ofDays(item.getDaysBeforeEvent()));
            if (!deadline.isAfter(now.plus(Duration.ofHours(1)))) {
                deadline = event.getStartsAt();
            }
            List<User> assignees = List.of();
            if (req.assignToDepartments() && item.getDepartment() != null) {
                assignees = users.findByDepartmentIdAndActiveTrue(item.getDepartment().getId());
            }
            Task task = taskService.createInternal(template.getClub(), creator, me.name(), event, item.getDepartment(),
                    item.getTitle(), item.getDescription(), item.getPriority(), deadline, assignees);
            created.add(task.getId());
        }
        audit.log(me, template.getClub().getId(), "TEMPLATE_APPLIED", "EVENT", event.getId(), template.getName(),
                created.size() + " tasks");
        return new ApplyResult(created.size(), created);
    }

    private void apply(TaskTemplate t, TemplateRequest req, UUID clubId) {
        t.setName(req.name().trim());
        t.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        t.getItems().clear();
        int position = 0;
        for (ItemRequest r : req.items()) {
            TaskTemplateItem item = new TaskTemplateItem();
            item.setTemplate(t);
            item.setTitle(r.title().trim());
            item.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
            item.setPriority(r.priority());
            item.setDaysBeforeEvent(r.daysBeforeEvent());
            item.setPosition(position++);
            if (r.departmentId() != null) {
                Department d = departments.findById(r.departmentId())
                        .orElseThrow(() -> ApiException.badRequest("Unknown department."));
                if (!d.getClub().getId().equals(clubId)) {
                    throw ApiException.badRequest("That department belongs to another club.");
                }
                item.setDepartment(d);
            }
            t.getItems().add(item);
        }
    }

    private TaskTemplate requireAccessible(AuthUser me, UUID id) {
        TaskTemplate t = templates.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (!me.sameClub(t.getClub().getId())) {
            throw ApiException.notFound("Template not found.");
        }
        return t;
    }

    private TemplateDto toDto(TaskTemplate t) {
        List<ItemDto> items = t.getItems().stream()
                .map(i -> new ItemDto(i.getId(), i.getTitle(), i.getDescription(), i.getPriority(),
                        i.getDepartment() == null ? null : i.getDepartment().getId(),
                        i.getDepartment() == null ? null : i.getDepartment().getName(), i.getDaysBeforeEvent()))
                .toList();
        return new TemplateDto(t.getId(), t.getClub().getId(), t.getName(), t.getDescription(), items);
    }
}
