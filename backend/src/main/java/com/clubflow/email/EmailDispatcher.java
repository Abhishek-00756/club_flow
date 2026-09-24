package com.clubflow.email;

import com.clubflow.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers queued emails. Request handlers only insert rows into email_outbox; this worker sends them,
 * so a slow or unavailable mail provider never slows down or breaks an API call. Failed sends are
 * retried with exponential backoff (1, 2, 4, 8 ... minutes) until app.mail.max-attempts is reached.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatcher {
    private final EmailOutboxRepository outbox;
    private final JavaMailSender mailSender;
    private final AppProperties props;

    @Scheduled(fixedDelayString = "${app.mail.dispatch-interval-ms}", initialDelay = 10000)
    public void dispatch() {
        if (!props.mail().enabled()) {
            return;
        }
        for (EmailOutbox row : outbox.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                EmailStatus.PENDING, Instant.now())) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(props.mail().from());
                helper.setTo(row.getToEmail());
                helper.setSubject(row.getSubject());
                helper.setText(row.getHtmlBody(), true);
                mailSender.send(message);
                row.setStatus(EmailStatus.SENT);
                row.setSentAt(Instant.now());
                row.setLastError(null);
            } catch (Exception ex) {
                int attempts = row.getAttempts() + 1;
                row.setAttempts(attempts);
                row.setLastError(truncate(ex.getMessage()));
                if (attempts >= props.mail().maxAttempts()) {
                    row.setStatus(EmailStatus.FAILED);
                } else {
                    row.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(1L << (attempts - 1))));
                }
                log.warn("Email to {} failed (attempt {}): {}", row.getToEmail(), attempts, ex.getMessage());
            }
            outbox.save(row);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "Unknown error";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
