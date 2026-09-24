package com.clubflow.event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, UUID> {
    List<EventAttendee> findByEventIdIn(Collection<UUID> eventIds);

    List<EventAttendee> findByEventId(UUID eventId);

    Optional<EventAttendee> findByEventIdAndUserId(UUID eventId, UUID userId);
}
