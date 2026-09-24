package com.clubflow.user;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<UserDto> list(@AuthenticationPrincipal AuthUser me,
                              @RequestParam(required = false) UUID clubId,
                              @RequestParam(required = false) Role role,
                              @RequestParam(required = false) UUID departmentId,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) Boolean active) {
        return service.list(me, clubId, role, departmentId, q, active);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UserRequests.UpdateProfile req) {
        return service.updateProfile(me, req);
    }

    @GetMapping("/{id}")
    public UserDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public UserDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UserRequests.CreateUser req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody UserRequests.UpdateUser req) {
        return service.update(me, id, req);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void resetPassword(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                              @Valid @RequestBody UserRequests.ResetPassword req) {
        service.resetPassword(me, id, req.newPassword());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }
}
