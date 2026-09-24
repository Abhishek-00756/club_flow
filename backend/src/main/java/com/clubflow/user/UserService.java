package com.clubflow.user;

import com.clubflow.audit.AuditService;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.Department;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.common.ApiException;
import com.clubflow.security.AuthUser;
import com.clubflow.security.RefreshTokenRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository users;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<UserDto> list(AuthUser me, UUID requestedClubId, Role role, UUID departmentId, String q,
                              Boolean active) {
        UUID clubId = me.scopeClub(requestedClubId);
        List<User> base = clubId != null ? users.findByClubId(clubId) : users.findAll();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> active == null || u.isActive() == active)
                .filter(u -> departmentId == null
                        || (u.getDepartment() != null && departmentId.equals(u.getDepartment().getId())))
                .filter(u -> needle.isEmpty() || u.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((User u) -> u.getRole().rank()).reversed()
                        .thenComparing(u -> u.getName().toLowerCase(Locale.ROOT)))
                .map(UserDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(AuthUser me, UUID id) {
        User u = load(id);
        if (!me.id().equals(id) && !(me.can(Permission.USER_VIEW) && me.sameClub(clubIdOf(u)))) {
            throw ApiException.notFound("User not found.");
        }
        return UserDto.from(u);
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser me) {
        return UserDto.from(load(me.id()), permissions.permissionsFor(me.role()));
    }

    public UserDto create(AuthUser me, UserRequests.CreateUser req) {
        assertCanAssignRole(me, req.role());
        Club club = null;
        if (req.role() != Role.SUPERADMIN) {
            UUID clubId = me.requireClub(req.clubId());
            club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Someone already uses this email address.");
        }
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        u.setClub(club);
        u.setDepartment(resolveDepartment(req.departmentId(), club));
        users.save(u);
        audit.log(me, club == null ? null : club.getId(), "USER_CREATED", "USER", u.getId(), null,
                u.getEmail() + " as " + u.getRole());
        return UserDto.from(u);
    }

    public UserDto update(AuthUser me, UUID id, UserRequests.UpdateUser req) {
        User u = load(id);
        assertCanManage(me, u);
        boolean self = me.id().equals(id);
        UUID clubId = clubIdOf(u);

        if (req.role() != u.getRole()) {
            if (!me.can(Permission.USER_ROLE_UPDATE)) {
                throw ApiException.forbidden("Your role cannot change roles.");
            }
            if (self) {
                throw ApiException.badRequest("You cannot change your own role.");
            }
            assertCanAssignRole(me, req.role());
            if (req.role() == Role.SUPERADMIN || u.getRole() == Role.SUPERADMIN) {
                throw ApiException.badRequest("SuperAdmin accounts cannot be created by changing a role.");
            }
            audit.log(me, clubId, "USER_ROLE_CHANGED", "USER", id, u.getRole(), req.role());
            u.setRole(req.role());
        }
        if (req.active() != u.isActive()) {
            if (self) {
                throw ApiException.badRequest("You cannot deactivate your own account.");
            }
            audit.log(me, clubId, req.active() ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", id, u.isActive(),
                    req.active());
            u.setActive(req.active());
            if (!req.active()) {
                refreshTokens.revokeAllForUser(id);
            }
        }
        u.setName(req.name().trim());
        u.setDepartment(resolveDepartment(req.departmentId(), u.getClub()));
        u.setUpdatedAt(Instant.now());
        audit.log(me, clubId, "USER_UPDATED", "USER", id, null, u.getName());
        return UserDto.from(u);
    }

    public void resetPassword(AuthUser me, UUID id, String newPassword) {
        User u = load(id);
        if (!me.id().equals(id)) {
            assertCanManage(me, u);
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setUpdatedAt(Instant.now());
        refreshTokens.revokeAllForUser(id);
        audit.log(me, clubIdOf(u), "PASSWORD_RESET", "USER", id, null, null);
    }

    public void delete(AuthUser me, UUID id) {
        User u = load(id);
        assertCanManage(me, u);
        if (me.id().equals(id)) {
            throw ApiException.badRequest("You cannot delete your own account.");
        }
        try {
            users.delete(u);
            users.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("This person has tasks or events on record. Deactivate them instead.");
        }
        audit.log(me, clubIdOf(u), "USER_DELETED", "USER", id, u.getEmail(), null);
    }

    public UserDto updateProfile(AuthUser me, UserRequests.UpdateProfile req) {
        User u = load(me.id());
        u.setName(req.name().trim());
        if (req.emailNotifications() != null) {
            u.setEmailNotifications(req.emailNotifications());
        }
        u.setUpdatedAt(Instant.now());
        return UserDto.from(u, permissions.permissionsFor(u.getRole()));
    }

    // ---- rules ----

    private void assertCanAssignRole(AuthUser me, Role role) {
        if (me.isSuperAdmin()) {
            return;
        }
        if (!me.role().outranks(role)) {
            throw ApiException.forbidden("You can only assign roles below your own.");
        }
    }

    private void assertCanManage(AuthUser me, User target) {
        if (me.isSuperAdmin()) {
            return;
        }
        if (!me.sameClub(clubIdOf(target))) {
            throw ApiException.notFound("User not found.");
        }
        if (!me.id().equals(target.getId()) && !me.role().outranks(target.getRole())) {
            throw ApiException.forbidden("You can only manage people whose role is below yours.");
        }
    }

    private Department resolveDepartment(UUID departmentId, Club club) {
        if (departmentId == null) {
            return null;
        }
        Department d = departments.findById(departmentId)
                .orElseThrow(() -> ApiException.badRequest("That department does not exist."));
        if (club == null || !d.getClub().getId().equals(club.getId())) {
            throw ApiException.badRequest("That department belongs to another club.");
        }
        return d;
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
    }

    private UUID clubIdOf(User u) {
        return u.getClub() == null ? null : u.getClub().getId();
    }
}
