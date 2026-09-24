package com.clubflow.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByClubIdOrderByStartsAtAsc(UUID clubId);

    List<Event> findByClubIdAndEndsAtAfterOrderByStartsAtAsc(UUID clubId, Instant after);

    List<Event> findAllByOrderByStartsAtAsc();

    List<Event> findByEndsAtAfterOrderByStartsAtAsc(Instant after);

    long countByClubIdAndEndsAtAfter(UUID clubId, Instant after);
}
