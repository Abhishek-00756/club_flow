package com.clubflow.notification;

import com.clubflow.common.ApiException;
import com.clubflow.config.AppProperties;
import com.clubflow.email.EmailOutbox;
import com.clubflow.email.EmailOutboxRepository;
import com.clubflow.email.EmailTemplates;
import com.clubflow.security.AuthUser;
import com.clubflow.user.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the in-app notification and, when the person wants email, a row in the email outbox.
 * Both are written in the caller's transaction, so they only exist if the underlying change committed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final AppProperties props;

    public record NotificationDto(UUID id, NotificationType type, String title, String message, String link,
                                  boolean read, Instant createdAt) {}

    public void notify(User to, NotificationType type, String title, String message, String link) {
        if (to == null || !to.isActive()) {
            return;
        }
        Notification n = new Notification();
        n.setUser(to);
        n.setType(type);
        n.setTitle(truncate(title, 200));
        n.setMessage(message);
        n.setLink(link);
        notifications.save(n);

        if (props.mail().enabled() && to.isEmailNotifications()) {
            String actionUrl = link == null ? null : props.frontendUrl() + link;
            EmailOutbox mail = new EmailOutbox();
            mail.setToEmail(to.getEmail());
            mail.setSubject(truncate(title, 250));
            mail.setHtmlBody(EmailTemplates.render(title, message, actionUrl, actionLabel(type)));
            outbox.save(mail);
        }
    }

    public void notifyAll(Collection<User> recipients, NotificationType type, String title, String message,
                          String link) {
        for (User u : recipients) {
            notify(u, type, title, message, link);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(AuthUser me, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        List<Notification> rows = unreadOnly
                ? notifications.findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(me.id(), page)
                : notifications.findByUserIdOrderByCreatedAtDesc(me.id(), page);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return notifications.countByUserIdAndReadFlagFalse(me.id());
    }

    public void markRead(AuthUser me, UUID id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getUser().getId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        n.setReadFlag(true);
    }

    public void markAllRead(AuthUser me) {
        notifications.markAllRead(me.id());
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.isReadFlag(),
                n.getCreatedAt());
    }

    private static String actionLabel(NotificationType type) {
        return switch (type) {
            case ANNOUNCEMENT -> "Read announcement";
            case EVENT_VOLUNTEER -> "View event";
            case SYSTEM -> "Open ClubFlow";
            default -> "Open task";
        };
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
