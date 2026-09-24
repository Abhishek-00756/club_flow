package com.clubflow.automation;

import com.clubflow.task.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record ItemRequest(@NotBlank @Size(max = 200) String title,
                              @Size(max = 5000) String description,
                              @NotNull Priority priority,
                              UUID departmentId,
                              @Min(0) @Max(365) int daysBeforeEvent) {}

    public record TemplateRequest(@NotBlank @Size(max = 120) String name,
                                  @Size(max = 2000) String description,
                                  @NotEmpty @Size(max = 40) List<@Valid ItemRequest> items,
                                  UUID clubId) {}

    public record ItemDto(UUID id, String title, String description, Priority priority, UUID departmentId,
                          String departmentName, int daysBeforeEvent) {}

    public record TemplateDto(UUID id, UUID clubId, String name, String description, List<ItemDto> items) {}

    public record ApplyRequest(@NotNull UUID eventId, boolean assignToDepartments) {}

    public record ApplyResult(int created, List<UUID> taskIds) {}
}
