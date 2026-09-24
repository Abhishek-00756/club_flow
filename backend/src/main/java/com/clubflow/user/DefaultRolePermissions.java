package com.clubflow.user;

import static com.clubflow.user.Permission.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Starting point for role permissions. SuperAdmin can edit these at runtime from the Roles page. */
public final class DefaultRolePermissions {
    private DefaultRolePermissions() {}

    public static Map<Role, Set<Permission>> all() {
        Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);

        Set<Permission> admin = EnumSet.allOf(Permission.class);
        admin.remove(CLUB_MANAGE);
        admin.remove(SYSTEM_CONFIG);
        map.put(Role.ADMIN, admin);

        map.put(Role.SECRETARY, EnumSet.of(
                TASK_CREATE, TASK_ASSIGN, TASK_UPDATE, TASK_APPROVE, TASK_VIEW_ALL, TASK_UPDATE_ASSIGNED, TASK_SUBMIT,
                USER_VIEW, EVENT_VIEW, EVENT_CREATE, EVENT_UPDATE, ANNOUNCEMENT_CREATE, TEMPLATE_MANAGE, REPORT_VIEW));

        map.put(Role.JOINT_SECRETARY, EnumSet.of(
                TASK_CREATE, TASK_ASSIGN, TASK_UPDATE, TASK_VIEW_ALL, TASK_UPDATE_ASSIGNED, TASK_SUBMIT,
                USER_VIEW, EVENT_VIEW, EVENT_CREATE));

        map.put(Role.MEMBER, EnumSet.of(
                TASK_VIEW_ASSIGNED, TASK_UPDATE_ASSIGNED, TASK_SUBMIT, EVENT_VIEW));

        return map;
    }
}
