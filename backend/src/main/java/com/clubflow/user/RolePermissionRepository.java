package com.clubflow.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    void deleteByRole(Role role);
}
