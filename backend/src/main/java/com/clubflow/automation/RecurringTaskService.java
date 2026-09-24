package com.clubflow.automation;

import com.clubflow.audit.AuditService;
import com.clubflow.automation.RecurringDtos.RecurringDto;
import com.clubflow.automation.RecurringDtos.RecurringRequest;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.Department;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.common.ApiException;
import com.clubflow.config.AppProperties;
import com.clubflow.security.AuthUser;
import com.clubflow.task.TaskService;
import com.clubflow.user.User;
import com.clubflow.user.UserRef;
import com.clubflow.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for recurring tasks, plus the per-schedule run that the daily scheduler triggers. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RecurringTaskService {
    private final RecurringTaskRepository repo;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final TaskService taskService;
    private final AuditService audit;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public List<RecurringDto> list(AuthUser me, UUID requestedClubId) {
        UUID clubId = me.scopeClub(requestedClubId);
        List<RecurringTask> rows = clubId == null ? repo.findAllByOrderByTitleAsc()
                : repo.findByClubIdOrderByTitleAsc(clubId);
        return rows.stream().map(this::toDto).toList();
    }

    public RecurringDto create(AuthUser me, RecurringRequest req) {
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        RecurringTask r = new RecurringTask();
        r.setClub(club);
        r.setCreatedBy(users.getReferenceById(me.id()));
        apply(r, req, clubId);
        repo.save(r);
        audit.log(me, clubId, "RECURRING_CREATED", "RECURRING_TASK", r.getId(), null, r.getTitle());
        return toDto(r);
    }

    public RecurringDto update(AuthUser me, UUID id, RecurringRequest req) {
        RecurringTask r = requireAccessible(me, id);
        String before = r.getTitle();
        apply(r, req, r.getClub().getId());
        audit.log(me, r.getClub().getId(), "RECURRING_UPDATED", "RECURRING_TASK", id, before, r.getTitle());
        return toDto(r);
    }

    public RecurringDto setActive(AuthUser me, UUID id, boolean active) {
        RecurringTask r = requireAccessible(me, id);
        r.setActive(active);
        audit.log(me, r.getClub().getId(), active ? "RECURRING_RESUMED" : "RECURRING_PAUSED", "RECURRING_TASK", id, null,
                r.getTitle());
        return toDto(r);
    }

    public void delete(AuthUser me, UUID id) {
        RecurringTask r = requireAccessible(me, id);
        repo.delete(r);
        audit.log(me, r.getClub().getId(), "RECURRING_DELETED", "RECURRING_TASK", id, r.getTitle(), null);
    }

    /**
     * Creates today's task for one schedule. Returns true if a task was created. The claim makes
     * this safe to call from several app instances or repeatedly during the same day.
     */
    public boolean runIfDue(UUID id, LocalDate today) {
        if (repo.claimRun(id, today) == 0) {
            return false;
        }
        RecurringTask r = repo.findById(id).orElse(null);
        if (r == null || !r.isDueOn(today)) {
            return false;
        }
        ZoneId zone = ZoneId.of(props.timezone());
        Instant deadline = today.plusDays(r.getDueInDays()).atTime(r.getDueTime()).atZone(zone).toInstant();
        if (!deadline.isAfter(Instant.now())) {
            deadline = Instant.now().plusSeconds(3600);
        }
        List<User> assignees = r.getAssignees().stream().filter(User::isActive)
                .filter(u -> u.getClub() != null && u.getClub().getId().equals(r.getClub().getId())).toList();
        User creator = r.getCreatedBy();
        taskService.createInternal(r.getClub(), creator, creator.getName(), null, r.getDepartment(), r.getTitle(),
                r.getDescription(), r.getPriority(), deadline, assignees);
        log.info("Recurring task '{}' created for {}", r.getTitle(), today);
        return true;
    }

    private void apply(RecurringTask r, RecurringRequest req, UUID clubId) {
        switch (req.frequency()) {
            case WEEKLY -> {
                if (req.dayOfWeek() == null) {
                    throw ApiException.badRequest("Pick the day of the week.");
                }
            }
            case MONTHLY -> {
                if (req.dayOfMonth() == null) {
                    throw ApiException.badRequest("Pick the day of the month.");
                }
            }
            default -> { }
        }
        r.setTitle(req.title().trim());
        r.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        r.setPriority(req.priority());
        r.setFrequency(req.frequency());
        r.setDayOfWeek(req.frequency() == Frequency.WEEKLY ? req.dayOfWeek() : null);
        r.setDayOfMonth(req.frequency() == Frequency.MONTHLY ? req.dayOfMonth() : null);
        r.setDueInDays(req.dueInDays());
        r.setDueTime(req.dueTime());
        if (req.active() != null) {
            r.setActive(req.active());
        }
        Department d = null;
        if (req.departmentId() != null) {
            d = departments.findById(req.departmentId()).orElseThrow(() -> ApiException.badRequest("Unknown department."));
            if (!d.getClub().getId().equals(clubId)) {
                throw ApiException.badRequest("That department belongs to another club.");
            }
        }
        r.setDepartment(d);

        List<UUID> ids = req.assigneeIds() == null ? List.<UUID>of() : req.assigneeIds().stream().filter(Objects::nonNull).distinct().toList();
        List<User> found = users.findAllById(ids);
        boolean ok = found.size() == ids.size() && found.stream()
                .allMatch(u -> u.isActive() && u.getClub() != null && u.getClub().getId().equals(clubId));
        if (!ok) {
            throw ApiException.badRequest("Everyone assigned must be an active member of this club.");
        }
        r.setAssignees(new LinkedHashSet<>(found));
    }

    private RecurringTask requireAccessible(AuthUser me, UUID id) {
        RecurringTask r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Recurring task not found."));
        if (!me.sameClub(r.getClub().getId())) {
            throw ApiException.notFound("Recurring task not found.");
        }
        return r;
    }

    private RecurringDto toDto(RecurringTask r) {
        return new RecurringDto(r.getId(), r.getClub().getId(), r.getTitle(), r.getDescription(), r.getPriority(),
                r.getFrequency(), r.getDayOfWeek(), r.getDayOfMonth(), r.getDueInDays(), r.getDueTime(),
                r.getDepartment() == null ? null : r.getDepartment().getId(),
                r.getDepartment() == null ? null : r.getDepartment().getName(), r.isActive(), r.getLastRunOn(),
                r.getAssignees().stream().sorted(Comparator.comparing(u -> u.getName().toLowerCase()))
                        .map(UserRef::of).toList());
    }
}
