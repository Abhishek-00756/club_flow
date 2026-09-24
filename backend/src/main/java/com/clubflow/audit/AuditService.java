package com.clubflow.audit;

import com.clubflow.common.PageResponse;
import com.clubflow.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records who changed what. Writes join the caller's transaction, so an action that is rolled back
 * leaves no audit row behind either.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditService {
    private final AuditLogRepository repo;

    public record AuditLogDto(UUID id, UUID clubId, UUID userId, String userName, String action, String entityType,
                              String entityId, String oldValue, String newValue, String ipAddress,
                              Instant createdAt) {}

    public void log(AuthUser actor, UUID clubId, String action, String entityType, Object entityId,
                    Object oldValue, Object newValue) {
        logAs(clubId, actor == null ? null : actor.id(), actor == null ? "System" : actor.name(), action, entityType,
                entityId, oldValue, newValue);
    }

    public void logAs(UUID clubId, UUID userId, String userName, String action, String entityType, Object entityId,
                      Object oldValue, Object newValue) {
        AuditLog row = new AuditLog();
        row.setClubId(clubId);
        row.setUserId(userId);
        row.setUserName(userName);
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityId(entityId == null ? null : String.valueOf(entityId));
        row.setOldValue(oldValue == null ? null : String.valueOf(oldValue));
        row.setNewValue(newValue == null ? null : String.valueOf(newValue));
        row.setIpAddress(currentIp());
        repo.save(row);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(AuthUser me, UUID requestedClubId, String entityType, String action,
                                            UUID userId, int page, int size) {
        UUID clubId = me.scopeClub(requestedClubId);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (clubId != null) {
                p.add(cb.equal(root.get("clubId"), clubId));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action));
            }
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = repo.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("createdAt").descending()));
        List<AuditLogDto> content = result.getContent().stream()
                .map(a -> new AuditLogDto(a.getId(), a.getClubId(), a.getUserId(), a.getUserName(), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getOldValue(), a.getNewValue(), a.getIpAddress(),
                        a.getCreatedAt()))
                .toList();
        return PageResponse.of(result, content);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
