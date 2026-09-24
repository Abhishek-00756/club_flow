package com.clubflow.club;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByClubIdOrderByNameAsc(UUID clubId);

    boolean existsByClubIdAndNameIgnoreCase(UUID clubId, String name);
}
