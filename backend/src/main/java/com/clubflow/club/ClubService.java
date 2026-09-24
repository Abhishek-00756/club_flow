package com.clubflow.club;

import com.clubflow.audit.AuditService;
import com.clubflow.common.ApiException;
import com.clubflow.security.AuthUser;
import com.clubflow.user.Permission;
import com.clubflow.user.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClubService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<String> DEFAULT_DEPARTMENTS =
            List.of("Technical", "Design", "Marketing", "Content", "Events", "PR");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ClubDto> list(AuthUser me) {
        if (me.isSuperAdmin()) {
            return clubs.findAll().stream().map(c -> toDto(c, me)).toList();
        }
        if (me.clubId() == null) {
            return List.of();
        }
        return clubs.findById(me.clubId()).map(c -> List.of(toDto(c, me))).orElse(List.of());
    }

    public ClubDto create(AuthUser me, ClubRequests.CreateClub req) {
        String name = req.name().trim();
        if (clubs.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A club with this name already exists.");
        }
        Club club = new Club();
        club.setName(name);
        club.setDescription(req.description());
        club.setJoinCode(newJoinCode());
        clubs.save(club);
        for (String dept : DEFAULT_DEPARTMENTS) {
            Department d = new Department();
            d.setClub(club);
            d.setName(dept);
            departments.save(d);
        }
        audit.log(me, club.getId(), "CLUB_CREATED", "CLUB", club.getId(), null, name);
        return toDto(club, me);
    }

    public ClubDto update(AuthUser me, UUID id, ClubRequests.UpdateClub req) {
        Club club = requireAccessible(me, id);
        String oldName = club.getName();
        String name = req.name().trim();
        if (!name.equalsIgnoreCase(oldName) && clubs.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A club with this name already exists.");
        }
        club.setName(name);
        club.setDescription(req.description());
        if (req.active() != null && me.can(Permission.CLUB_MANAGE)) {
            club.setActive(req.active());
        }
        audit.log(me, club.getId(), "CLUB_UPDATED", "CLUB", club.getId(), oldName, name);
        return toDto(club, me);
    }

    public ClubDto regenerateJoinCode(AuthUser me, UUID id) {
        Club club = requireAccessible(me, id);
        club.setJoinCode(newJoinCode());
        audit.log(me, club.getId(), "CLUB_JOIN_CODE_REGENERATED", "CLUB", club.getId(), null, null);
        return toDto(club, me);
    }

    public void delete(AuthUser me, UUID id) {
        Club club = clubs.findById(id).orElseThrow(() -> ApiException.notFound("Club not found."));
        if (users.countByClubId(id) > 0) {
            throw ApiException.conflict("This club still has members. Deactivate it instead of deleting it.");
        }
        try {
            clubs.delete(club);
            clubs.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("This club still has tasks or events. Deactivate it instead of deleting it.");
        }
        audit.log(me, null, "CLUB_DELETED", "CLUB", id, club.getName(), null);
    }

    // ---- departments ----

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments(AuthUser me, UUID requestedClubId) {
        UUID clubId = me.scopeClub(requestedClubId);
        if (clubId == null) {
            throw ApiException.badRequest("Choose a club.");
        }
        return departments.findByClubIdOrderByNameAsc(clubId).stream().map(DepartmentDto::from).toList();
    }

    public DepartmentDto createDepartment(AuthUser me, ClubRequests.DepartmentRequest req) {
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        String name = req.name().trim();
        if (departments.existsByClubIdAndNameIgnoreCase(clubId, name)) {
            throw ApiException.conflict("This club already has a department with that name.");
        }
        Department d = new Department();
        d.setClub(club);
        d.setName(name);
        departments.save(d);
        audit.log(me, clubId, "DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), null, name);
        return DepartmentDto.from(d);
    }

    public DepartmentDto renameDepartment(AuthUser me, UUID id, ClubRequests.DepartmentRequest req) {
        Department d = requireDepartment(me, id);
        String old = d.getName();
        d.setName(req.name().trim());
        audit.log(me, d.getClub().getId(), "DEPARTMENT_UPDATED", "DEPARTMENT", id, old, d.getName());
        return DepartmentDto.from(d);
    }

    public void deleteDepartment(AuthUser me, UUID id) {
        Department d = requireDepartment(me, id);
        departments.delete(d);
        audit.log(me, d.getClub().getId(), "DEPARTMENT_DELETED", "DEPARTMENT", id, d.getName(), null);
    }

    // ---- helpers ----

    private Department requireDepartment(AuthUser me, UUID id) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department not found."));
        if (!me.sameClub(d.getClub().getId())) {
            throw ApiException.notFound("Department not found.");
        }
        return d;
    }

    private Club requireAccessible(AuthUser me, UUID id) {
        Club club = clubs.findById(id).orElseThrow(() -> ApiException.notFound("Club not found."));
        if (!me.sameClub(club.getId())) {
            throw ApiException.notFound("Club not found.");
        }
        return club;
    }

    private ClubDto toDto(Club c, AuthUser me) {
        String code = me.can(Permission.CLUB_UPDATE) ? c.getJoinCode() : null;
        return new ClubDto(c.getId(), c.getName(), c.getDescription(), code, c.isActive(),
                users.countByClubId(c.getId()), c.getCreatedAt());
    }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (clubs.existsByJoinCode(code));
        return code;
    }
}
