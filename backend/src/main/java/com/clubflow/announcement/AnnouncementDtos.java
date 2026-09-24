package com.clubflow.announcement;

import com.clubflow.user.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AnnouncementDtos {
    private AnnouncementDtos() {}

    public record AnnouncementRequest(@NotBlank @Size(max = 200) String title,
                                      @NotBlank @Size(max = 5000) String body,
                                      UUID clubId) {}

    public record AnnouncementDto(UUID id, UUID clubId, String title, String body, UserRef author, Instant createdAt) {}
}
