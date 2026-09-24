package com.clubflow.security;

import com.clubflow.common.ApiException;
import com.clubflow.user.Permission;
import com.clubflow.user.Role;
import java.util.Set;
import java.util.UUID;

/** The authenticated caller, rebuilt from the database on every request. */
public record AuthUser(UUID id, String name, String email, Role role, UUID clubId, Set<Permission> permissions) {

    public boolean isSuperAdmin() {
        return role == Role.SUPERADMIN;
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Club that a query should be limited to. SuperAdmin may pick any club (or none = all clubs);
     * everyone else is always confined to their own club.
     */
    public UUID scopeClub(UUID requested) {
        if (isSuperAdmin()) {
            return requested;
        }
        if (clubId == null) {
            throw ApiException.forbidden("Your account is not part of a club.");
        }
        if (requested != null && !requested.equals(clubId)) {
            throw ApiException.forbidden("You can only access your own club.");
        }
        return clubId;
    }

    /** Like scopeClub but for writes: a club must always be resolved. */
    public UUID requireClub(UUID requested) {
        UUID resolved = scopeClub(requested);
        if (resolved == null) {
            throw ApiException.badRequest("Choose a club first.");
        }
        return resolved;
    }

    public boolean sameClub(UUID otherClubId) {
        return isSuperAdmin() || (clubId != null && clubId.equals(otherClubId));
    }
}
