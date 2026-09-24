package com.clubflow.event;

import com.clubflow.user.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {}

    public record EventRequest(@NotBlank @Size(max = 200) String title,
                               @Size(max = 5000) String description,
                               @Size(max = 200) String location,
                               @NotNull Instant startsAt,
                               @NotNull Instant endsAt,
                               UUID clubId) {}

    public record EventDto(UUID id, UUID clubId, String title, String description, String location,
                           Instant startsAt, Instant endsAt, UserRef createdBy, long attendeeCount,
                           long volunteerCount, long attendedCount, long taskCount,
                           Participation myParticipation, boolean myAttended) {}

    public record AttendeeDto(UUID userId, String name, Participation participation, boolean attended,
                              Instant checkedInAt) {}

    public record EventDetail(EventDto event, List<AttendeeDto> attendees) {}

    public record JoinRequest(Participation participation) {}

    public record AddAttendeeRequest(@NotNull UUID userId, Participation participation) {}

    public record AttendanceRequest(@NotNull Boolean attended) {}
}
