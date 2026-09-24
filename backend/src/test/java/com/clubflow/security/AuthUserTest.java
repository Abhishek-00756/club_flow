package com.clubflow.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clubflow.common.ApiException;
import com.clubflow.user.Permission;
import com.clubflow.user.Role;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthUserTest {
    private final UUID clubA = UUID.randomUUID();
    private final UUID clubB = UUID.randomUUID();

    private AuthUser member(UUID club) {
        return new AuthUser(UUID.randomUUID(), "Member", "m@example.com", Role.MEMBER, club,
                EnumSet.of(Permission.TASK_VIEW_ASSIGNED));
    }

    private AuthUser superAdmin() {
        return new AuthUser(UUID.randomUUID(), "Root", "root@example.com", Role.SUPERADMIN, null,
                EnumSet.allOf(Permission.class));
    }

    @Test
    void membersAreAlwaysConfinedToTheirOwnClub() {
        AuthUser me = member(clubA);
        assertEquals(clubA, me.scopeClub(null));
        assertEquals(clubA, me.scopeClub(clubA));
        assertThrows(ApiException.class, () -> me.scopeClub(clubB));
    }

    @Test
    void aMemberWithoutAClubGetsNothing() {
        assertThrows(ApiException.class, () -> member(null).scopeClub(null));
    }

    @Test
    void superAdminMayPickAnyClubOrNone() {
        AuthUser root = superAdmin();
        assertNull(root.scopeClub(null));
        assertEquals(clubB, root.scopeClub(clubB));
        assertTrue(root.sameClub(clubA));
    }

    @Test
    void writesNeedAClubToBeChosen() {
        assertThrows(ApiException.class, () -> superAdmin().requireClub(null));
        assertEquals(clubA, superAdmin().requireClub(clubA));
    }

    @Test
    void sameClubIsFalseForOtherClubs() {
        assertFalse(member(clubA).sameClub(clubB));
        assertTrue(member(clubA).sameClub(clubA));
    }

    @Test
    void canChecksPermissions() {
        AuthUser me = member(clubA);
        assertTrue(me.can(Permission.TASK_VIEW_ASSIGNED));
        assertFalse(me.can(Permission.TASK_CREATE));
    }
}
