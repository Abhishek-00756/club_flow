package com.clubflow.user;

import com.clubflow.audit.AuditService;
import com.clubflow.common.ApiException;
import com.clubflow.security.AuthUser;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the role -> permissions map. It is cached in memory and refreshed whenever it changes,
 * so authorization checks never hit the database.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final RolePermissionRepository repo;
    private final AuditService audit;

    private volatile Map<Role, Set<Permission>> cache = DefaultRolePermissions.all();

    public Set<Permission> permissionsFor(Role role) {
        if (role == Role.SUPERADMIN) {
            return EnumSet.allOf(Permission.class);
        }
        return cache.getOrDefault(role, Set.of());
    }

    public Map<Role, Set<Permission>> snapshot() {
        Map<Role, Set<Permission>> copy = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            copy.put(role, permissionsFor(role));
        }
        return copy;
    }

    @Transactional
    public void seedDefaultsIfEmpty() {
        if (repo.count() == 0) {
            DefaultRolePermissions.all().forEach((role, perms) ->
                    perms.forEach(p -> repo.save(new RolePermission(role, p))));
            repo.flush();
        }
        reload();
    }

    @Transactional(readOnly = true)
    public void reload() {
        Map<Role, Set<Permission>> fresh = new EnumMap<>(Role.class);
        for (RolePermission rp : repo.findAll()) {
            fresh.computeIfAbsent(rp.getRole(), r -> EnumSet.noneOf(Permission.class)).add(rp.getPermission());
        }
        cache = fresh;
    }

    @Transactional
    public Map<Role, Set<Permission>> update(AuthUser me, Role role, Set<Permission> permissions) {
        if (role == Role.SUPERADMIN) {
            throw ApiException.badRequest("SuperAdmin always has every permission.");
        }
        Set<Permission> before = permissionsFor(role);
        repo.deleteByRole(role);
        repo.flush();
        for (Permission p : permissions) {
            repo.save(new RolePermission(role, p));
        }
        repo.flush();
        reload();
        audit.log(me, null, "PERMISSIONS_UPDATED", "ROLE", role.name(), before.toString(), permissions.toString());
        return snapshot();
    }
}
