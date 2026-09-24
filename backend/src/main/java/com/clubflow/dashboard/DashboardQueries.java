package com.clubflow.dashboard;

import com.clubflow.task.Task;
import com.clubflow.task.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Aggregate queries behind the dashboards. Each one is a single GROUP BY, so a dashboard costs a
 * handful of queries no matter how many tasks exist.
 */
@Component
class DashboardQueries {
    @PersistenceContext
    private EntityManager em;

    // ---- per person ----

    Map<TaskStatus, Long> statusCountsForUser(UUID userId) {
        return statusMap(em.createQuery("""
                select t.status, count(t) from Task t join t.assignees a
                where a.user.id = :uid group by t.status
                """, Object[].class).setParameter("uid", userId).getResultList());
    }

    long overdueForUser(UUID userId, Instant now) {
        return em.createQuery("""
                select count(t) from Task t join t.assignees a
                where a.user.id = :uid and t.status in :open and t.deadline < :now
                """, Long.class).setParameter("uid", userId).setParameter("open", TaskStatus.OPEN)
                .setParameter("now", now).getSingleResult();
    }

    long dueBetweenForUser(UUID userId, Instant from, Instant to) {
        return em.createQuery("""
                select count(t) from Task t join t.assignees a
                where a.user.id = :uid and t.status in :open and t.deadline >= :from and t.deadline < :to
                """, Long.class).setParameter("uid", userId).setParameter("open", TaskStatus.OPEN)
                .setParameter("from", from).setParameter("to", to).getSingleResult();
    }

    List<Task> upcomingForUser(UUID userId, int limit) {
        return em.createQuery("""
                select distinct t from Task t join t.assignees a
                where a.user.id = :uid and t.status in :open order by t.deadline asc
                """, Task.class).setParameter("uid", userId).setParameter("open", TaskStatus.OPEN)
                .setMaxResults(limit).getResultList();
    }

    // ---- per club ----

    Map<TaskStatus, Long> statusCounts(UUID clubId) {
        return statusMap(em.createQuery("""
                select t.status, count(t) from Task t where t.club.id = :c group by t.status
                """, Object[].class).setParameter("c", clubId).getResultList());
    }

    long overdueCount(UUID clubId, Instant now) {
        return em.createQuery("""
                select count(t) from Task t where t.club.id = :c and t.status in :open and t.deadline < :now
                """, Long.class).setParameter("c", clubId).setParameter("open", TaskStatus.OPEN)
                .setParameter("now", now).getSingleResult();
    }

    List<Task> overdueTasks(UUID clubId, Instant now, int limit) {
        return em.createQuery("""
                select t from Task t where t.club.id = :c and t.status in :open and t.deadline < :now
                order by t.deadline asc
                """, Task.class).setParameter("c", clubId).setParameter("open", TaskStatus.OPEN)
                .setParameter("now", now).setMaxResults(limit).getResultList();
    }

    List<Task> awaitingReview(UUID clubId, int limit) {
        return em.createQuery("""
                select t from Task t where t.club.id = :c and t.status in :review
                order by t.submittedAt asc nulls last
                """, Task.class).setParameter("c", clubId).setParameter("review", TaskStatus.AWAITING_REVIEW)
                .setMaxResults(limit).getResultList();
    }

    /** department id -> (status -> count) */
    Map<UUID, Map<TaskStatus, Long>> departmentStatusCounts(UUID clubId) {
        Map<UUID, Map<TaskStatus, Long>> result = new HashMap<>();
        for (Object[] row : em.createQuery("""
                select t.department.id, t.status, count(t) from Task t
                where t.club.id = :c and t.department is not null group by t.department.id, t.status
                """, Object[].class).setParameter("c", clubId).getResultList()) {
            result.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(TaskStatus.class))
                    .put((TaskStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    Map<UUID, Long> departmentOverdue(UUID clubId, Instant now) {
        return idCounts(em.createQuery("""
                select t.department.id, count(t) from Task t
                where t.club.id = :c and t.department is not null and t.status in :open and t.deadline < :now
                group by t.department.id
                """, Object[].class).setParameter("c", clubId).setParameter("open", TaskStatus.OPEN)
                .setParameter("now", now).getResultList());
    }

    /** user id -> (status -> count), for people who have at least one task in this club. */
    Map<UUID, Map<TaskStatus, Long>> memberStatusCounts(UUID clubId) {
        Map<UUID, Map<TaskStatus, Long>> result = new HashMap<>();
        for (Object[] row : em.createQuery("""
                select a.user.id, t.status, count(t) from Task t join t.assignees a
                where t.club.id = :c group by a.user.id, t.status
                """, Object[].class).setParameter("c", clubId).getResultList()) {
            result.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(TaskStatus.class))
                    .put((TaskStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    Map<UUID, Long> memberOverdue(UUID clubId, Instant now) {
        return idCounts(em.createQuery("""
                select a.user.id, count(t) from Task t join t.assignees a
                where t.club.id = :c and t.status in :open and t.deadline < :now group by a.user.id
                """, Object[].class).setParameter("c", clubId).setParameter("open", TaskStatus.OPEN)
                .setParameter("now", now).getResultList());
    }

    List<Instant> createdSince(UUID clubId, Instant since) {
        return em.createQuery("select t.createdAt from Task t where t.club.id = :c and t.createdAt >= :s",
                Instant.class).setParameter("c", clubId).setParameter("s", since).getResultList();
    }

    List<Instant> completedSince(UUID clubId, Instant since) {
        return em.createQuery("""
                select t.completedAt from Task t
                where t.club.id = :c and t.status = com.clubflow.task.TaskStatus.COMPLETED and t.completedAt >= :s
                """, Instant.class).setParameter("c", clubId).setParameter("s", since).getResultList();
    }

    // ---- system wide ----

    /** club id -> (status -> count) */
    Map<UUID, Map<TaskStatus, Long>> statusCountsByClub() {
        Map<UUID, Map<TaskStatus, Long>> result = new HashMap<>();
        for (Object[] row : em.createQuery("""
                select t.club.id, t.status, count(t) from Task t group by t.club.id, t.status
                """, Object[].class).getResultList()) {
            result.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(TaskStatus.class))
                    .put((TaskStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    Map<UUID, Long> overdueByClub(Instant now) {
        return idCounts(em.createQuery("""
                select t.club.id, count(t) from Task t where t.status in :open and t.deadline < :now
                group by t.club.id
                """, Object[].class).setParameter("open", TaskStatus.OPEN).setParameter("now", now).getResultList());
    }

    Map<UUID, Long> activeMembersByClub() {
        return idCounts(em.createQuery("""
                select u.club.id, count(u) from User u where u.active = true and u.club is not null group by u.club.id
                """, Object[].class).getResultList());
    }

    // ---- helpers ----

    private static Map<TaskStatus, Long> statusMap(List<Object[]> rows) {
        Map<TaskStatus, Long> map = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            map.put(s, 0L);
        }
        for (Object[] row : rows) {
            map.put((TaskStatus) row[0], (Long) row[1]);
        }
        return map;
    }

    private static Map<UUID, Long> idCounts(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }
}
