package com.clubflow.task;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecs {
    private TaskSpecs() {}

    public static Specification<Task> matching(UUID clubId, TaskStatus status, Priority priority, UUID departmentId,
                                               UUID eventId, UUID assigneeId, boolean overdueOnly, String q,
                                               Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (clubId != null) {
                p.add(cb.equal(root.get("club").get("id"), clubId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                p.add(cb.equal(root.get("priority"), priority));
            }
            if (departmentId != null) {
                p.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (eventId != null) {
                p.add(cb.equal(root.get("event").get("id"), eventId));
            }
            if (assigneeId != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<TaskAssignee> ta = sub.from(TaskAssignee.class);
                sub.select(ta.<UUID>get("id"))
                        .where(cb.equal(ta.get("task"), root), cb.equal(ta.get("user").get("id"), assigneeId));
                p.add(cb.exists(sub));
            }
            if (overdueOnly) {
                p.add(root.get("status").in(TaskStatus.OPEN));
                p.add(cb.lessThan(root.<Instant>get("deadline"), now));
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.<String>get("title")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
