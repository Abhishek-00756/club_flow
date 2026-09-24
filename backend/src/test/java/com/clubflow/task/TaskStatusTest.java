package com.clubflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class TaskStatusTest {

    @Test
    void openMeansTheAssigneeStillHasWorkToDo() {
        assertEquals(EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), TaskStatus.OPEN);
    }

    @Test
    void activeIncludesReviewButNotFinishedOrCancelled() {
        assertTrue(TaskStatus.SUBMITTED.isActive());
        assertTrue(TaskStatus.UNDER_REVIEW.isActive());
        assertFalse(TaskStatus.COMPLETED.isActive());
        assertFalse(TaskStatus.CANCELLED.isActive());
    }

    @Test
    void submittedWorkIsNeverOverdue() {
        Task task = new Task();
        task.setDeadline(Instant.now().minusSeconds(3600));

        task.setStatus(TaskStatus.IN_PROGRESS);
        assertTrue(task.isOverdue(Instant.now()));

        task.setStatus(TaskStatus.SUBMITTED);
        assertFalse(task.isOverdue(Instant.now()));
    }

    @Test
    void movingTheDeadlineResetsReminders() {
        Task task = new Task();
        task.setReminderFirstSent(true);
        task.setReminderSecondSent(true);
        task.setOverdueNotified(true);

        task.resetReminders();

        assertFalse(task.isReminderFirstSent());
        assertFalse(task.isReminderSecondSent());
        assertFalse(task.isOverdueNotified());
    }
}
