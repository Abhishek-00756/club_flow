package com.clubflow.announcement;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    @EntityGraph(attributePaths = "author")
    Page<Announcement> findByClubIdOrderByCreatedAtDesc(UUID clubId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
