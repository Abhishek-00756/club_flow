package com.clubflow.club;

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
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService service;

    @GetMapping("/clubs")
    public List<ClubDto> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @PostMapping("/clubs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLUB_MANAGE')")
    public ClubDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ClubRequests.CreateClub req) {
        return service.create(me, req);
    }

    @PutMapping("/clubs/{id}")
    @PreAuthorize("hasAuthority('CLUB_UPDATE')")
    public ClubDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody ClubRequests.UpdateClub req) {
        return service.update(me, id, req);
    }

    @PostMapping("/clubs/{id}/join-code")
    @PreAuthorize("hasAuthority('CLUB_UPDATE')")
    public ClubDto regenerate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.regenerateJoinCode(me, id);
    }

    @DeleteMapping("/clubs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CLUB_MANAGE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments(@AuthenticationPrincipal AuthUser me,
                                           @RequestParam(required = false) UUID clubId) {
        return service.listDepartments(me, clubId);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDto createDepartment(@AuthenticationPrincipal AuthUser me,
                                          @Valid @RequestBody ClubRequests.DepartmentRequest req) {
        return service.createDepartment(me, req);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDto renameDepartment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                          @Valid @RequestBody ClubRequests.DepartmentRequest req) {
        return service.renameDepartment(me, id, req);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    public void deleteDepartment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.deleteDepartment(me, id);
    }
}
