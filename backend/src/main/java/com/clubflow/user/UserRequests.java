package com.clubflow.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserRequests {
    private UserRequests() {}

    public record CreateUser(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            UUID departmentId,
            UUID clubId) {}

    public record UpdateUser(
            @NotBlank @Size(max = 120) String name,
            @NotNull Role role,
            UUID departmentId,
            @NotNull Boolean active) {}

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record UpdateProfile(@NotBlank @Size(max = 120) String name, Boolean emailNotifications) {}
}
