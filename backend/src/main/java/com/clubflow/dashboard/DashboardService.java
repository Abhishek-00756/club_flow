package com.clubflow.dashboard;

import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.common.ApiException;
import com.clubflow.config.AppProperties;
import com.clubflow.dashboard.DashboardDtos.ClubDashboard;
import com.clubflow.dashboard.DashboardDtos.ClubStat;
import com.clubflow.dashboard.DashboardDtos.DepartmentStat;
import com.clubflow.dashboard.DashboardDtos.MemberDashboard;
import com.clubflow.dashboard.DashboardDtos.MemberStat;
import com.clubflow.dashboard.DashboardDtos.OverdueRow;
import com.clubflow.dashboard.DashboardDtos.ReviewRow;
import com.clubflow.dashboard.DashboardDtos.SuperAdminDashboard;
import com.clubflow.dashboard.DashboardDtos.WeekPoint;
import com.clubflow.email.EmailOutboxRepository;
import com.clubflow.email.EmailStatus;
import com.clubflow.event.EventRepository;
import com.clubflow.event.EventService;
import com.clubflow.security.AuthUser;
import com.clubflow.task.Task;
import com.clubflow.task.TaskService;
import com.clubflow.task.TaskStatus;
import com.clubflow.user.User;
import com.clubflow.user.UserRef;
import com.clubflow.user.UserRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private static final int WEEKS = 8;

    private final DashboardQueries queries;
    private final TaskService taskService;
    private final EventService eventService;
    private final EventRepository events;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final EmailOutboxRepository outbox;
    private final AppProperties props;

    public MemberDashboard member(AuthUser me) {
        ZoneId zone = ZoneId.of(props.timezone());
        Instant now = Instant.now();
        Instant endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();

        Map<TaskStatus, Long> byStatus = queries.statusCountsForUser(me.id());
        long active = TaskStatus.ACTIVE.stream().mapToLong(byStatus::get).sum();
        long review = TaskStatus.AWAITING_REVIEW.stream().mapToLong(byStatus::get).sum();

        List<Task> upcoming = queries.upcomingForUser(me.id(), 8);
        return new MemberDashboard(active, queries.dueBetweenForUser(me.id(), now, endOfToday),
                byStatus.get(TaskStatus.COMPLETED), queries.overdueForUser(me.id(), now), review,
                taskService.toDtos(upcoming), me.clubId() == null ? List.of() : eventService.upcoming(me, 5));
    }

    public ClubDashboard club(AuthUser me, UUID requestedClubId) {
        UUID clubId = me.requireClub(requestedClubId);
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        ZoneId zone = ZoneId.of(props.timezone());
        Instant now = Instant.now();

        Map<TaskStatus, Long> byStatus = queries.statusCounts(clubId);
        long active = TaskStatus.ACTIVE.stream().mapToLong(byStatus::get).sum();
        long review = TaskStatus.AWAITING_REVIEW.stream().mapToLong(byStatus::get).sum();

        List<OverdueRow> overdueRows = new ArrayList<>();
        for (Task t : queries.overdueTasks(clubId, now, 10)) {
            long hours = Math.max(Duration.between(t.getDeadline(), now).toHours(), 0);
            if (t.getAssignees().isEmpty()) {
                overdueRows.add(new OverdueRow(t.getId(), t.getTitle(), null, hours));
            }
            t.getAssignees().forEach(a ->
                    overdueRows.add(new OverdueRow(t.getId(), t.getTitle(), UserRef.of(a.getUser()), hours)));
        }
        List<ReviewRow> reviewRows = queries.awaitingReview(clubId, 10).stream()
                .map(t -> new ReviewRow(t.getId(), t.getTitle(),
                        t.getAssignees().stream().map(a -> UserRef.of(a.getUser())).toList(), t.getSubmittedAt()))
                .toList();

        return new ClubDashboard(clubId, club.getName(), users.countByClubIdAndActiveTrue(clubId), active,
                byStatus.get(TaskStatus.COMPLETED), queries.overdueCount(clubId, now), review,
                events.findByClubIdAndEndsAtAfterOrderByStartsAtAsc(clubId, now).size(), byStatus, overdueRows,
                reviewRows, departmentStats(clubId, now), workload(clubId, now), weekly(clubId, zone));
    }

    public SuperAdminDashboard superAdmin() {
        Instant now = Instant.now();
        Map<UUID, Map<TaskStatus, Long>> statuses = queries.statusCountsByClub();
        Map<UUID, Long> overdue = queries.overdueByClub(now);
        Map<UUID, Long> members = queries.activeMembersByClub();

        List<ClubStat> stats = new ArrayList<>();
        long active = 0;
        long completed = 0;
        long overdueTotal = 0;
        for (Club c : clubs.findAll()) {
            Map<TaskStatus, Long> s = statuses.getOrDefault(c.getId(), new EnumMap<>(TaskStatus.class));
            long clubActive = TaskStatus.ACTIVE.stream().mapToLong(x -> s.getOrDefault(x, 0L)).sum();
            long clubDone = s.getOrDefault(TaskStatus.COMPLETED, 0L);
            long clubOverdue = overdue.getOrDefault(c.getId(), 0L);
            active += clubActive;
            completed += clubDone;
            overdueTotal += clubOverdue;
            stats.add(new ClubStat(c.getId(), c.getName(), members.getOrDefault(c.getId(), 0L), clubActive, clubDone,
                    clubOverdue));
        }
        stats.sort(Comparator.comparing(ClubStat::name, String.CASE_INSENSITIVE_ORDER));
        return new SuperAdminDashboard(stats.size(), users.count(), active, completed, overdueTotal,
                outbox.countByStatus(EmailStatus.SENT), outbox.countByStatus(EmailStatus.PENDING),
                outbox.countByStatus(EmailStatus.FAILED), stats);
    }

    // ---- pieces ----

    private List<DepartmentStat> departmentStats(UUID clubId, Instant now) {
        Map<UUID, Map<TaskStatus, Long>> counts = queries.departmentStatusCounts(clubId);
        Map<UUID, Long> overdue = queries.departmentOverdue(clubId, now);
        return departments.findByClubIdOrderByNameAsc(clubId).stream().map(d -> {
            Map<TaskStatus, Long> s = counts.getOrDefault(d.getId(), Map.of());
            long active = TaskStatus.ACTIVE.stream().mapToLong(x -> s.getOrDefault(x, 0L)).sum();
            return new DepartmentStat(d.getId(), d.getName(), active, s.getOrDefault(TaskStatus.COMPLETED, 0L),
                    overdue.getOrDefault(d.getId(), 0L));
        }).toList();
    }

    /** Everyone active in the club, busiest first, so people with spare capacity are easy to spot at the bottom. */
    private List<MemberStat> workload(UUID clubId, Instant now) {
        Map<UUID, Map<TaskStatus, Long>> counts = queries.memberStatusCounts(clubId);
        Map<UUID, Long> overdue = queries.memberOverdue(clubId, now);
        List<MemberStat> rows = new ArrayList<>();
        for (User u : users.findByClubIdAndActiveTrue(clubId)) {
            Map<TaskStatus, Long> s = counts.getOrDefault(u.getId(), Map.of());
            long open = TaskStatus.OPEN.stream().mapToLong(x -> s.getOrDefault(x, 0L)).sum();
            long inReview = TaskStatus.AWAITING_REVIEW.stream().mapToLong(x -> s.getOrDefault(x, 0L)).sum();
            rows.add(new MemberStat(u.getId(), u.getName(), open, inReview, overdue.getOrDefault(u.getId(), 0L),
                    s.getOrDefault(TaskStatus.COMPLETED, 0L)));
        }
        rows.sort(Comparator.comparingLong((MemberStat m) -> m.open() + m.inReview()).reversed()
                .thenComparing(MemberStat::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Tasks created vs completed for each of the last eight weeks (weeks start on Monday, club time). */
    private List<WeekPoint> weekly(UUID clubId, ZoneId zone) {
        LocalDate thisWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(WEEKS - 1L);
        Instant since = first.atStartOfDay(zone).toInstant();

        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (int i = 0; i < WEEKS; i++) {
            buckets.put(first.plusWeeks(i), new long[2]);
        }
        for (Instant i : queries.createdSince(clubId, since)) {
            long[] b = buckets.get(weekStart(i, zone));
            if (b != null) {
                b[0]++;
            }
        }
        for (Instant i : queries.completedSince(clubId, since)) {
            long[] b = buckets.get(weekStart(i, zone));
            if (b != null) {
                b[1]++;
            }
        }
        return buckets.entrySet().stream()
                .map(e -> new WeekPoint(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    private static LocalDate weekStart(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
