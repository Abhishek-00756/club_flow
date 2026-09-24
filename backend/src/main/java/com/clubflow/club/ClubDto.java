package com.clubflow.club;

import java.time.Instant;
import java.util.UUID;

public record ClubDto(UUID id, String name, String description, String joinCode, boolean active,
                      long memberCount, Instant createdAt) {
}
