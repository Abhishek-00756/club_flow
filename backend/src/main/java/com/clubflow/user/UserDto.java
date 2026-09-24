package com.clubflow.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record UserDto(UUID id, String name, String email, Role role, UUID clubId, String clubName,
                      UUID departmentId, String departmentName, boolean active, boolean emailNotifications,
                      Instant createdAt, Instant lastLoginAt, List<String> permissions) {

    public static UserDto from(User u, Collection<Permission> permissions) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.getClub() == null ? null : u.getClub().getId(),
                u.getClub() == null ? null : u.getClub().getName(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getDepartment() == null ? null : u.getDepartment().getName(),
                u.isActive(), u.isEmailNotifications(), u.getCreatedAt(), u.getLastLoginAt(),
                permissions.stream().map(Enum::name).sorted().toList());
    }

    public static UserDto from(User u) {
        return from(u, List.of());
    }
}
