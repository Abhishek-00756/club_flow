package com.clubflow.automation;

import com.clubflow.automation.RecurringDtos.RecurringDto;
import com.clubflow.automation.RecurringDtos.RecurringRequest;
import com.clubflow.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
public class RecurringTaskController {
    private final RecurringTaskService service;

    @GetMapping
    public List<RecurringDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID clubId) {
        return service.list(me, clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RecurringRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    public RecurringDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                               @Valid @RequestBody RecurringRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/{id}/pause")
    public RecurringDto pause(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setActive(me, id, false);
    }

    @PostMapping("/{id}/resume")
    public RecurringDto resume(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setActive(me, id, true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }
}
