package com.clubflow.user;

import java.util.UUID;

/** Lightweight reference to a user, used inside other DTOs. */
public record UserRef(UUID id, String name) {
    public static UserRef of(User u) {
        return u == null ? null : new UserRef(u.getId(), u.getName());
    }
}
