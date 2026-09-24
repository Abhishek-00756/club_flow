package com.clubflow.user;

import com.clubflow.security.AuthUser;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService service;

    public record PermissionMatrix(List<String> permissions, Map<String, List<String>> roles) {}

    public record UpdateRolePermissions(Set<Permission> permissions) {}

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public PermissionMatrix matrix() {
        return toMatrix(service.snapshot());
    }

    @PutMapping("/{role}")
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public PermissionMatrix update(@AuthenticationPrincipal AuthUser me, @PathVariable Role role,
                                   @RequestBody UpdateRolePermissions body) {
        Set<Permission> perms = body.permissions() == null ? Set.of() : body.permissions();
        return toMatrix(service.update(me, role, perms));
    }

    private PermissionMatrix toMatrix(Map<Role, Set<Permission>> map) {
        Map<String, List<String>> roles = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            roles.put(role.name(), map.getOrDefault(role, Set.of()).stream().map(Enum::name).sorted().toList());
        }
        List<String> all = Arrays.stream(Permission.values()).map(Enum::name).toList();
        return new PermissionMatrix(all, roles);
    }
}
