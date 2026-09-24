package com.clubflow.audit;

import com.clubflow.common.PageResponse;
import com.clubflow.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService service;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditService.AuditLogDto> search(@AuthenticationPrincipal AuthUser me,
                                                         @RequestParam(required = false) UUID clubId,
                                                         @RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) String action,
                                                         @RequestParam(required = false) UUID userId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return service.search(me, clubId, entityType, action, userId, page, size);
    }
}
