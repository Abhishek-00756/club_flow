package com.clubflow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByClubId(UUID clubId);

    List<User> findByClubIdAndActiveTrue(UUID clubId);

    long countByClubId(UUID clubId);

    long countByClubIdAndActiveTrue(UUID clubId);

    List<User> findByRole(Role role);

    List<User> findByDepartmentIdAndActiveTrue(UUID departmentId);
}
