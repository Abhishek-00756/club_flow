package com.clubflow.task;

import java.util.EnumSet;
import java.util.Set;

/**
 * TODO -> IN_PROGRESS -> SUBMITTED -> UNDER_REVIEW -> COMPLETED, with CANCELLED as a side exit.
 * A reviewer can also send SUBMITTED / UNDER_REVIEW work back to IN_PROGRESS.
 */
public enum TaskStatus {
    TODO, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, COMPLETED, CANCELLED;

    /** Work the assignee still has to do (deadline reminders and "overdue" only apply here). */
    public static final Set<TaskStatus> OPEN = EnumSet.of(TODO, IN_PROGRESS);

    /** Everything that is not finished or cancelled. */
    public static final Set<TaskStatus> ACTIVE = EnumSet.of(TODO, IN_PROGRESS, SUBMITTED, UNDER_REVIEW);

    public static final Set<TaskStatus> AWAITING_REVIEW = EnumSet.of(SUBMITTED, UNDER_REVIEW);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
