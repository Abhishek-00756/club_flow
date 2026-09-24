package com.clubflow.automation;

import com.clubflow.automation.TemplateDtos.ApplyRequest;
import com.clubflow.automation.TemplateDtos.ApplyResult;
import com.clubflow.automation.TemplateDtos.TemplateDto;
import com.clubflow.automation.TemplateDtos.TemplateRequest;
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
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private static final String READ = "hasAnyAuthority('TEMPLATE_MANAGE','TASK_CREATE')";

    private final TemplateService service;

    @GetMapping
    @PreAuthorize(READ)
    public List<TemplateDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID clubId) {
        return service.list(me, clubId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public TemplateDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    public TemplateDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TemplateRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    public TemplateDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                              @Valid @RequestBody TemplateRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TEMPLATE_MANAGE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    public ApplyResult apply(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody ApplyRequest req) {
        return service.applyToEvent(me, id, req);
    }
}
