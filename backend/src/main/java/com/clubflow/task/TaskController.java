package com.clubflow.task;

import com.clubflow.common.PageResponse;
import com.clubflow.security.AuthUser;
import com.clubflow.task.TaskDtos.AssignRequest;
import com.clubflow.task.TaskDtos.CommentRequest;
import com.clubflow.task.TaskDtos.NoteRequest;
import com.clubflow.task.TaskDtos.TaskDetail;
import com.clubflow.task.TaskDtos.TaskDto;
import com.clubflow.task.TaskDtos.TaskFilter;
import com.clubflow.task.TaskDtos.TaskRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Coarse permission gates live here; the finer rules (is this person assigned? is the task in the
 * right status?) live in {@link TaskService}, which is the only place that decides.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private static final String VIEW = "hasAnyAuthority('TASK_VIEW_ALL','TASK_VIEW_ASSIGNED')";

    private final TaskService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public PageResponse<TaskDto> list(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) UUID clubId,
                                      @RequestParam(required = false) TaskStatus status,
                                      @RequestParam(required = false) Priority priority,
                                      @RequestParam(required = false) UUID departmentId,
                                      @RequestParam(required = false) UUID eventId,
                                      @RequestParam(required = false) UUID assigneeId,
                                      @RequestParam(required = false) Boolean mine,
                                      @RequestParam(required = false) Boolean overdue,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "deadline") String sort,
                                      @RequestParam(defaultValue = "false") boolean desc,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        TaskFilter filter = new TaskFilter(clubId, status, priority, departmentId, eventId, assigneeId, mine,
                overdue, q);
        return service.list(me, filter, sort, desc, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public TaskDetail get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    public TaskDetail create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TaskRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public TaskDetail update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody TaskRequest req) {
        return service.update(me, id, req);
    }

    @PutMapping("/{id}/assignees")
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public TaskDetail assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody AssignRequest req) {
        return service.assign(me, id, req.userIds());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TASK_DELETE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    // ---- workflow ----

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('TASK_UPDATE_ASSIGNED')")
    public TaskDetail start(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.start(me, id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('TASK_SUBMIT')")
    public TaskDetail submit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody(required = false) NoteRequest body) {
        return service.submit(me, id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('TASK_APPROVE')")
    public TaskDetail startReview(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.startReview(me, id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('TASK_APPROVE')")
    public TaskDetail approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                              @Valid @RequestBody(required = false) NoteRequest body) {
        return service.approve(me, id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('TASK_APPROVE')")
    public TaskDetail reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody NoteRequest body) {
        return service.reject(me, id, body.note());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public TaskDetail cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody(required = false) NoteRequest body) {
        return service.cancel(me, id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public TaskDetail reopen(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.reopen(me, id);
    }

    // ---- comments & files ----

    @PostMapping("/{id}/comments")
    @PreAuthorize(VIEW)
    public TaskDetail comment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                              @Valid @RequestBody CommentRequest body) {
        return service.comment(me, id, body.body());
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(VIEW)
    public TaskDetail upload(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @RequestPart("file") MultipartFile file) {
        return service.upload(me, id, file);
    }

    /** Always sent as a download so uploaded HTML or SVG can never run inside the app's origin. */
    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(VIEW)
    public ResponseEntity<org.springframework.core.io.Resource> download(@AuthenticationPrincipal AuthUser me,
                                                                         @PathVariable UUID id,
                                                                         @PathVariable UUID attachmentId) {
        TaskService.Download file = service.download(me, id, attachmentId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.contentType());
        } catch (Exception ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(VIEW)
    public TaskDetail deleteAttachment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                       @PathVariable UUID attachmentId) {
        return service.deleteAttachment(me, id, attachmentId);
    }
}
