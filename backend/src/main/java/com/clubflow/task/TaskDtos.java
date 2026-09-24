package com.clubflow.task;

import com.clubflow.user.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}

    public record TaskRequest(@NotBlank @Size(max = 200) String title,
                              @Size(max = 5000) String description,
                              @NotNull Priority priority,
                              @NotNull Instant deadline,
                              UUID departmentId,
                              UUID eventId,
                              List<UUID> assigneeIds,
                              UUID clubId) {}

    public record TaskDto(UUID id, String title, String description, Priority priority, TaskStatus status,
                          Instant deadline, Instant createdAt, Instant updatedAt, Instant submittedAt,
                          Instant completedAt, String reviewNote, boolean overdue, UUID clubId,
                          UUID departmentId, String departmentName, UUID eventId, String eventTitle,
                          UserRef createdBy, List<UserRef> assignees, long commentCount, long attachmentCount) {}

    public record TaskFilter(UUID clubId, TaskStatus status, Priority priority, UUID departmentId, UUID eventId,
                             UUID assigneeId, Boolean mine, Boolean overdue, String q) {}

    public record NoteRequest(@Size(max = 2000) String note) {}

    public record CommentRequest(@NotBlank @Size(max = 4000) String body) {}

    public record CommentDto(UUID id, String body, UserRef author, Instant createdAt) {}

    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, UserRef uploadedBy,
                                Instant createdAt) {}

    public record AssignRequest(@NotNull List<UUID> userIds) {}

    /** What the signed-in person may do with this task right now; the UI renders buttons from this. */
    public record TaskActions(boolean edit, boolean assign, boolean start, boolean submit, boolean startReview,
                              boolean review, boolean cancel, boolean reopen, boolean delete, boolean attach) {}

    public record TaskDetail(TaskDto task, List<CommentDto> comments, List<AttachmentDto> attachments,
                             TaskActions actions) {}
}
