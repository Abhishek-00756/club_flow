package com.clubflow.event;

import com.clubflow.audit.AuditService;
import com.clubflow.common.ApiException;
import com.clubflow.event.EventDtos.AttendeeDto;
import com.clubflow.event.EventDtos.EventDetail;
import com.clubflow.event.EventDtos.EventDto;
import com.clubflow.event.EventDtos.EventRequest;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.notification.NotificationService;
import com.clubflow.notification.NotificationType;
import com.clubflow.security.AuthUser;
import com.clubflow.task.TaskRepository;
import com.clubflow.user.User;
import com.clubflow.user.UserRef;
import com.clubflow.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {
    private static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofHours(1);

    private final EventRepository events;
    private final EventAttendeeRepository attendees;
    private final ClubRepository clubs;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final NotificationService notifications;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<EventDto> list(AuthUser me, UUID requestedClubId, boolean upcomingOnly) {
        UUID clubId = me.scopeClub(requestedClubId);
        Instant now = Instant.now();
        List<Event> rows;
        if (clubId != null) {
            rows = upcomingOnly ? events.findByClubIdAndEndsAtAfterOrderByStartsAtAsc(clubId, now)
                    : events.findByClubIdOrderByStartsAtAsc(clubId);
        } else {
            rows = upcomingOnly ? events.findByEndsAtAfterOrderByStartsAtAsc(now) : events.findAllByOrderByStartsAtAsc();
        }
        return toDtos(rows, me);
    }

    @Transactional(readOnly = true)
    public List<EventDto> upcoming(AuthUser me, int limit) {
        UUID clubId = me.scopeClub(null);
        if (clubId == null) {
            return List.of();
        }
        List<Event> rows = events.findByClubIdAndEndsAtAfterOrderByStartsAtAsc(clubId, Instant.now());
        return toDtos(rows.stream().limit(limit).toList(), me);
    }

    @Transactional(readOnly = true)
    public EventDetail get(AuthUser me, UUID id) {
        Event e = requireAccessible(me, id);
        List<AttendeeDto> people = attendees.findByEventId(id).stream()
                .sorted(Comparator.comparing((EventAttendee a) -> a.getParticipation().ordinal())
                        .thenComparing(a -> a.getUser().getName().toLowerCase()))
                .map(a -> new AttendeeDto(a.getUser().getId(), a.getUser().getName(), a.getParticipation(),
                        a.isAttended(), a.getCheckedInAt()))
                .toList();
        return new EventDetail(toDtos(List.of(e), me).get(0), people);
    }

    public EventDto create(AuthUser me, EventRequest req) {
        validateTimes(req);
        UUID clubId = me.requireClub(req.clubId());
        Club club = clubs.findById(clubId).orElseThrow(() -> ApiException.notFound("Club not found."));
        Event e = new Event();
        e.setClub(club);
        e.setCreatedBy(users.getReferenceById(me.id()));
        apply(e, req);
        events.save(e);
        audit.log(me, clubId, "EVENT_CREATED", "EVENT", e.getId(), null, e.getTitle());
        return toDtos(List.of(e), me).get(0);
    }

    public EventDto update(AuthUser me, UUID id, EventRequest req) {
        validateTimes(req);
        Event e = requireAccessible(me, id);
        String before = e.getTitle() + " @ " + e.getStartsAt();
        apply(e, req);
        audit.log(me, e.getClub().getId(), "EVENT_UPDATED", "EVENT", id, before, e.getTitle() + " @ " + e.getStartsAt());
        return toDtos(List.of(e), me).get(0);
    }

    public void delete(AuthUser me, UUID id) {
        Event e = requireAccessible(me, id);
        events.delete(e);
        audit.log(me, e.getClub().getId(), "EVENT_DELETED", "EVENT", id, e.getTitle(), null);
    }

    // ---- attendance ----

    public EventDto join(AuthUser me, UUID eventId, Participation participation) {
        Event e = requireAccessible(me, eventId);
        if (attendees.findByEventIdAndUserId(eventId, me.id()).isEmpty()) {
            EventAttendee a = new EventAttendee();
            a.setEvent(e);
            a.setUser(users.getReferenceById(me.id()));
            a.setParticipation(participation == null ? Participation.PARTICIPANT : participation);
            attendees.save(a);
        }
        return toDtos(List.of(e), me).get(0);
    }

    public EventDto leave(AuthUser me, UUID eventId) {
        Event e = requireAccessible(me, eventId);
        attendees.findByEventIdAndUserId(eventId, me.id()).ifPresent(attendees::delete);
        return toDtos(List.of(e), me).get(0);
    }

    public EventDto checkIn(AuthUser me, UUID eventId) {
        Event e = requireAccessible(me, eventId);
        Instant now = Instant.now();
        if (now.isBefore(e.getStartsAt().minus(CHECK_IN_OPENS_BEFORE)) || now.isAfter(e.getEndsAt())) {
            throw ApiException.badRequest("Check-in is open from one hour before the event until it ends.");
        }
        EventAttendee a = attendees.findByEventIdAndUserId(eventId, me.id()).orElseGet(() -> {
            EventAttendee fresh = new EventAttendee();
            fresh.setEvent(e);
            fresh.setUser(users.getReferenceById(me.id()));
            return attendees.save(fresh);
        });
        a.setAttended(true);
        a.setCheckedInAt(now);
        return toDtos(List.of(e), me).get(0);
    }

    public EventDetail addAttendee(AuthUser me, UUID eventId, EventDtos.AddAttendeeRequest req) {
        Event e = requireAccessible(me, eventId);
        User u = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User not found."));
        if (u.getClub() == null || !u.getClub().getId().equals(e.getClub().getId())) {
            throw ApiException.badRequest("That person is not in this club.");
        }
        EventAttendee a = attendees.findByEventIdAndUserId(eventId, u.getId()).orElseGet(() -> {
            EventAttendee fresh = new EventAttendee();
            fresh.setEvent(e);
            fresh.setUser(u);
            return fresh;
        });
        a.setParticipation(req.participation() == null ? Participation.VOLUNTEER : req.participation());
        attendees.save(a);
        if (!u.getId().equals(me.id())) {
            notifications.notify(u, NotificationType.EVENT_VOLUNTEER, "You are on the list for " + e.getTitle(),
                    me.name() + " added you to " + e.getTitle() + " as " + a.getParticipation().name().toLowerCase() + ".",
                    "/events/" + e.getId());
        }
        return get(me, eventId);
    }

    public EventDetail setAttendance(AuthUser me, UUID eventId, UUID userId, boolean attended) {
        requireAccessible(me, eventId);
        EventAttendee a = attendees.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> ApiException.notFound("That person is not on the attendee list."));
        a.setAttended(attended);
        a.setCheckedInAt(attended ? Instant.now() : null);
        return get(me, eventId);
    }

    public EventDetail removeAttendee(AuthUser me, UUID eventId, UUID userId) {
        requireAccessible(me, eventId);
        attendees.findByEventIdAndUserId(eventId, userId).ifPresent(attendees::delete);
        return get(me, eventId);
    }

    // ---- helpers ----

    public Event requireAccessible(AuthUser me, UUID id) {
        Event e = events.findById(id).orElseThrow(() -> ApiException.notFound("Event not found."));
        if (!me.sameClub(e.getClub().getId())) {
            throw ApiException.notFound("Event not found.");
        }
        return e;
    }

    public List<EventDto> toDtos(List<Event> rows, AuthUser me) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Event::getId).toList();
        Map<UUID, List<EventAttendee>> byEvent = attendees.findByEventIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getEvent().getId()));
        Map<UUID, Long> taskCounts = new HashMap<>();
        for (Object[] row : tasks.countByEventIds(ids)) {
            taskCounts.put((UUID) row[0], (Long) row[1]);
        }
        return rows.stream().map(e -> {
            List<EventAttendee> people = byEvent.getOrDefault(e.getId(), List.of());
            EventAttendee mine = people.stream().filter(a -> a.getUser().getId().equals(me.id())).findFirst().orElse(null);
            return new EventDto(e.getId(), e.getClub().getId(), e.getTitle(), e.getDescription(), e.getLocation(),
                    e.getStartsAt(), e.getEndsAt(), UserRef.of(e.getCreatedBy()), people.size(),
                    people.stream().filter(a -> a.getParticipation() == Participation.VOLUNTEER).count(),
                    people.stream().filter(EventAttendee::isAttended).count(),
                    taskCounts.getOrDefault(e.getId(), 0L),
                    mine == null ? null : mine.getParticipation(), mine != null && mine.isAttended());
        }).toList();
    }

    private void validateTimes(EventRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw ApiException.badRequest("The event must end after it starts.");
        }
    }

    private void apply(Event e, EventRequest req) {
        e.setTitle(req.title().trim());
        e.setDescription(req.description());
        e.setLocation(req.location());
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
    }
}
