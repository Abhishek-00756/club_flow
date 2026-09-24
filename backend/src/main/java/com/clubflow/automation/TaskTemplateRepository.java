package com.clubflow.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {
    List<TaskTemplate> findByClubIdOrderByNameAsc(UUID clubId);

    List<TaskTemplate> findAllByOrderByNameAsc();
}
