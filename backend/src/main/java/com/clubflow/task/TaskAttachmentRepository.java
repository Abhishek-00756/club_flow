package com.clubflow.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {
    List<TaskAttachment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    @Query("select a.task.id, count(a) from TaskAttachment a where a.task.id in :ids group by a.task.id")
    List<Object[]> countByTaskIds(@Param("ids") Collection<UUID> ids);
}
