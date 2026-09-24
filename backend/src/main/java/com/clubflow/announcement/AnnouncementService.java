package com.clubflow.announcement;

import com.clubflow.announcement.AnnouncementDtos.AnnouncementDto;
import com.clubflow.announcement.AnnouncementDtos.AnnouncementRequest;
import com.clubflow.audit.AuditService;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.common.ApiException;
import com.clubflow.common.PageResponse;
import com.clubflow.notification.NotificationService;
import com.clubflow.notification.NotificationType;
import com.clubflow.security.AuthUser;
import com.clubflow.user.Permission;
import com.clubflow.user.UserRef;
import com.clubflow.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementService {
    private final AnnouncementRepository repo;
    private final ClubRepository clubs;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDto> list(AuthUser me, UUID requestedClubId, int page, int size) {
        UUID clubId = me.scopeClub(requestedClubId);
        PageRequest paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Announcement> result = clubId == null ? repo.findAllByOrderByCreatedAtDesc(paging)
                : repo.findByClubIdOrderByCreatedAtDesc(clubId, paging);
        return PageResponse.of(result, result.getContent().stream().map(this::toDto).toList());
    }

    /** Saves the announcement and queues an in-app notification (and email, if wanted) for every active member. */
    public AnnouncementDto create(AuthUser me, AnnouncementRequest req) {
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        Announcement a = new Announcement();
        a.setClub(club);
        a.setAuthor(users.getReferenceById(me.id()));
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        repo.save(a);
        audit.log(me, clubId, "ANNOUNCEMENT_CREATED", "ANNOUNCEMENT", a.getId(), null, a.getTitle());

        notifications.notifyAll(users.findByClubIdAndActiveTrue(clubId).stream()
                        .filter(u -> !u.getId().equals(me.id())).toList(),
                NotificationType.ANNOUNCEMENT, a.getTitle(), me.name() + ": " + abbreviate(a.getBody(), 240),
                "/announcements");
        return toDto(a);
    }

    public void delete(AuthUser me, UUID id) {
        Announcement a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Announcement not found."));
        if (!me.sameClub(a.getClub().getId())) {
            throw ApiException.notFound("Announcement not found.");
        }
        boolean isAuthor = a.getAuthor().getId().equals(me.id());
        if (!isAuthor && !me.can(Permission.CLUB_UPDATE)) {
            throw ApiException.forbidden("Only the author or a club admin can remove an announcement.");
        }
        repo.delete(a);
        audit.log(me, a.getClub().getId(), "ANNOUNCEMENT_DELETED", "ANNOUNCEMENT", id, a.getTitle(), null);
    }

    private AnnouncementDto toDto(Announcement a) {
        return new AnnouncementDto(a.getId(), a.getClub().getId(), a.getTitle(), a.getBody(), UserRef.of(a.getAuthor()),
                a.getCreatedAt());
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
