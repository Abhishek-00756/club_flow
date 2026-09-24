package com.clubflow.club;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, UUID> {
    Optional<Club> findByJoinCodeIgnoreCase(String joinCode);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByJoinCode(String joinCode);
}
