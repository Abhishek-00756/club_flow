package com.clubflow.automation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTaskRepository extends JpaRepository<RecurringTask, UUID> {
    List<RecurringTask> findByClubIdOrderByTitleAsc(UUID clubId);

    List<RecurringTask> findAllByOrderByTitleAsc();

    @Query("select r.id from RecurringTask r where r.active = true and (r.lastRunOn is null or r.lastRunOn < :today)")
    List<UUID> findIdsNotRunOn(@Param("today") LocalDate today);

    /** Returns 1 for the one caller that gets to run this schedule today (safe with several instances). */
    @Modifying
    @Query("update RecurringTask r set r.lastRunOn = :today "
            + "where r.id = :id and r.active = true and (r.lastRunOn is null or r.lastRunOn < :today)")
    int claimRun(@Param("id") UUID id, @Param("today") LocalDate today);
}
