package com.clubflow.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    @Query("select c.task.id, count(c) from TaskComment c where c.task.id in :ids group by c.task.id")
    List<Object[]> countByTaskIds(@Param("ids") Collection<UUID> ids);
}
