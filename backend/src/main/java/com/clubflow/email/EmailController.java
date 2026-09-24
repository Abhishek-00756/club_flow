package com.clubflow.email;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/emails")
@RequiredArgsConstructor
public class EmailController {
    private final EmailOutboxRepository outbox;

    public record EmailLogDto(UUID id, String to, String subject, EmailStatus status, int attempts, String lastError,
                              Instant createdAt, Instant sentAt) {}

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public List<EmailLogDto> recent(@RequestParam(required = false) EmailStatus status) {
        List<EmailOutbox> rows = status == null
                ? outbox.findTop100ByOrderByCreatedAtDesc()
                : outbox.findTop100ByStatusOrderByCreatedAtDesc(status);
        return rows.stream()
                .map(e -> new EmailLogDto(e.getId(), e.getToEmail(), e.getSubject(), e.getStatus(), e.getAttempts(),
                        e.getLastError(), e.getCreatedAt(), e.getSentAt()))
                .toList();
    }
}
