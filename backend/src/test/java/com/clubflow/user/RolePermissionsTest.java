package com.clubflow.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePermissionsTest {
    private final Map<Role, Set<Permission>> defaults = DefaultRolePermissions.all();

    @Test
    void higherRolesOutrankLowerOnes() {
        assertTrue(Role.SUPERADMIN.outranks(Role.ADMIN));
        assertTrue(Role.SECRETARY.outranks(Role.JOINT_SECRETARY));
        assertFalse(Role.MEMBER.outranks(Role.MEMBER));
        assertFalse(Role.MEMBER.outranks(Role.SECRETARY));
    }

    @Test
    void membersOnlySeeAndWorkOnTheirOwnTasks() {
        Set<Permission> member = defaults.get(Role.MEMBER);
        assertTrue(member.contains(Permission.TASK_VIEW_ASSIGNED));
        assertTrue(member.contains(Permission.TASK_SUBMIT));
        assertFalse(member.contains(Permission.TASK_VIEW_ALL));
        assertFalse(member.contains(Permission.TASK_CREATE));
        assertFalse(member.contains(Permission.TASK_APPROVE));
    }

    @Test
    void secretaryReviewsButJointSecretaryDoesNot() {
        assertTrue(defaults.get(Role.SECRETARY).contains(Permission.TASK_APPROVE));
        assertFalse(defaults.get(Role.JOINT_SECRETARY).contains(Permission.TASK_APPROVE));
        assertTrue(defaults.get(Role.JOINT_SECRETARY).contains(Permission.TASK_ASSIGN));
    }

    @Test
    void adminManagesTheClubButNotTheSystem() {
        Set<Permission> admin = defaults.get(Role.ADMIN);
        assertTrue(admin.contains(Permission.USER_ROLE_UPDATE));
        assertTrue(admin.contains(Permission.AUDIT_VIEW));
        assertFalse(admin.contains(Permission.CLUB_MANAGE));
        assertFalse(admin.contains(Permission.SYSTEM_CONFIG));
    }
}
