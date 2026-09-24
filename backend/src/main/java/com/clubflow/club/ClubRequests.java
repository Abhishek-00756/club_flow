package com.clubflow.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ClubRequests {
    private ClubRequests() {}

    public record CreateClub(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description) {}

    public record UpdateClub(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description,
                             Boolean active) {}

    public record DepartmentRequest(@NotBlank @Size(max = 80) String name, UUID clubId) {}
}
