package com.clubflow.announcement;

import com.clubflow.announcement.AnnouncementDtos.AnnouncementDto;
import com.clubflow.announcement.AnnouncementDtos.AnnouncementRequest;
import com.clubflow.common.PageResponse;
import com.clubflow.security.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;

    @GetMapping
    public PageResponse<AnnouncementDto> list(@AuthenticationPrincipal AuthUser me,
                                              @RequestParam(required = false) UUID clubId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(me, clubId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_CREATE')")
    public AnnouncementDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AnnouncementRequest req) {
        return service.create(me, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_CREATE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }
}
