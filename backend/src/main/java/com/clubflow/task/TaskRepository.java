package com.clubflow.task;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /** Overrides the specification query so list views load their to-one relations in one go. */
    @Override
    @EntityGraph(attributePaths = {"createdBy", "department", "event"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    List<Task> findByClubId(UUID clubId);

    List<Task> findByEventId(UUID eventId);

    @Query("select distinct t from Task t join t.assignees a where a.user.id = :userId")
    List<Task> findAssignedTo(@Param("userId") UUID userId);

    @Query("select t.event.id, count(t) from Task t where t.event.id in :ids group by t.event.id")
    List<Object[]> countByEventIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select t from Task t
            where t.status in :statuses
              and t.deadline > :now and t.deadline <= :limit
              and (t.reminderFirstSent = false or t.reminderSecondSent = false)
            """)
    List<Task> findDueForReminder(@Param("statuses") Collection<TaskStatus> statuses, @Param("now") Instant now,
                                  @Param("limit") Instant limit);

    @Query("select t from Task t where t.status in :statuses and t.deadline <= :now and t.overdueNotified = false")
    List<Task> findNewlyOverdue(@Param("statuses") Collection<TaskStatus> statuses, @Param("now") Instant now);

    long countByClubIdAndStatusIn(UUID clubId, Collection<TaskStatus> statuses);

    long countByStatusIn(Collection<TaskStatus> statuses);

    // Reminder "claims": each returns 1 only for the caller that flips the flag, so if two app
    // instances scan at the same moment, only one of them sends the notification.

    @Modifying
    @Query("""
            update Task t set t.reminderFirstSent = true
            where t.id = :id and t.reminderFirstSent = false
              and t.status in (com.clubflow.task.TaskStatus.TODO, com.clubflow.task.TaskStatus.IN_PROGRESS)
            """)
    int claimFirstReminder(@Param("id") UUID id);

    @Modifying
    @Query("""
            update Task t set t.reminderFirstSent = true, t.reminderSecondSent = true
            where t.id = :id and t.reminderSecondSent = false
              and t.status in (com.clubflow.task.TaskStatus.TODO, com.clubflow.task.TaskStatus.IN_PROGRESS)
            """)
    int claimSecondReminder(@Param("id") UUID id);

    @Modifying
    @Query("""
            update Task t set t.overdueNotified = true
            where t.id = :id and t.overdueNotified = false
              and t.status in (com.clubflow.task.TaskStatus.TODO, com.clubflow.task.TaskStatus.IN_PROGRESS)
            """)
    int claimOverdue(@Param("id") UUID id);
}
