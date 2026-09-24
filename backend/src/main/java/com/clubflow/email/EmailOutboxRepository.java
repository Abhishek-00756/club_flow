package com.clubflow.email;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    List<EmailOutbox> findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(EmailStatus status, Instant before);

    List<EmailOutbox> findTop100ByOrderByCreatedAtDesc();

    List<EmailOutbox> findTop100ByStatusOrderByCreatedAtDesc(EmailStatus status);

    long countByStatus(EmailStatus status);
}
