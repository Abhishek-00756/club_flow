package com.clubflow.automation;

import com.clubflow.task.Priority;
import com.clubflow.user.UserRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class RecurringDtos {
    private RecurringDtos() {}

    public record RecurringRequest(@NotBlank @Size(max = 200) String title,
                                   @Size(max = 5000) String description,
                                   @NotNull Priority priority,
                                   @NotNull Frequency frequency,
                                   @Min(1) @Max(7) Integer dayOfWeek,
                                   @Min(1) @Max(31) Integer dayOfMonth,
                                   @Min(0) @Max(60) int dueInDays,
                                   @NotNull LocalTime dueTime,
                                   UUID departmentId,
                                   List<UUID> assigneeIds,
                                   Boolean active,
                                   UUID clubId) {}

    public record RecurringDto(UUID id, UUID clubId, String title, String description, Priority priority,
                               Frequency frequency, Integer dayOfWeek, Integer dayOfMonth, int dueInDays,
                               LocalTime dueTime, UUID departmentId, String departmentName, boolean active,
                               LocalDate lastRunOn, List<UserRef> assignees) {}
}
