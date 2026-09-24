package com.clubflow.automation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clubflow.config.AppProperties;
import com.clubflow.notification.NotificationService;
import com.clubflow.notification.NotificationType;
import com.clubflow.task.Task;
import com.clubflow.task.TaskAssignee;
import com.clubflow.task.TaskRepository;
import com.clubflow.task.TaskStatus;
import com.clubflow.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReminderProcessorTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private ReminderProcessor processor;

    private final UUID taskId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://localhost:5173", "http://localhost:5173", "Asia/Kolkata",
                new AppProperties.Jwt("secret", 15, 14), new AppProperties.Mail(false, "from@example.com", 10_000, 5),
                new AppProperties.Reminders(24, 3, 60_000), new AppProperties.Storage("./uploads"),
                new AppProperties.Seed(false, "n", "e@example.com", "p", false), new AppProperties.RateLimit(20));
        processor = new ReminderProcessor(tasks, notifications, props);
    }

    private void taskDueIn(Duration left, TaskStatus status) {
        User creator = new User();
        creator.setName("Priya");
        User assignee = new User();
        assignee.setName("Rahul");
        Task t = new Task();
        t.setTitle("Design poster");
        t.setStatus(status);
        t.setDeadline(now.plus(left));
        t.setCreatedBy(creator);
        t.getAssignees().add(new TaskAssignee(t, assignee));
        when(tasks.findById(taskId)).thenReturn(Optional.of(t));
    }

    @Test
    void farFromTheDeadlineSendsTheFirstReminder() {
        taskDueIn(Duration.ofHours(20), TaskStatus.IN_PROGRESS);
        when(tasks.claimFirstReminder(taskId)).thenReturn(1);

        processor.remind(taskId, now);

        verify(tasks).claimFirstReminder(taskId);
        verify(tasks, never()).claimSecondReminder(any());
        verify(notifications).notifyAll(anyCollection(), eq(NotificationType.TASK_REMINDER), any(), any(), any());
    }

    @Test
    void closeToTheDeadlineSendsTheUrgentReminderInstead() {
        taskDueIn(Duration.ofHours(2), TaskStatus.TODO);
        when(tasks.claimSecondReminder(taskId)).thenReturn(1);

        processor.remind(taskId, now);

        verify(tasks).claimSecondReminder(taskId);
        verify(tasks, never()).claimFirstReminder(any());
        verify(notifications).notifyAll(anyCollection(), eq(NotificationType.TASK_REMINDER), any(), any(), any());
    }

    @Test
    void whenAnotherInstanceAlreadyClaimedItNothingIsSent() {
        taskDueIn(Duration.ofHours(20), TaskStatus.TODO);
        when(tasks.claimFirstReminder(taskId)).thenReturn(0);

        processor.remind(taskId, now);

        verifyNoInteractions(notifications);
    }

    @Test
    void submittedWorkIsNotRemindedAbout() {
        taskDueIn(Duration.ofHours(2), TaskStatus.SUBMITTED);

        processor.remind(taskId, now);

        verify(tasks, never()).claimFirstReminder(any());
        verify(tasks, never()).claimSecondReminder(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void overdueTellsTheAssigneeAndTheCreator() {
        taskDueIn(Duration.ofHours(-5), TaskStatus.IN_PROGRESS);
        when(tasks.claimOverdue(taskId)).thenReturn(1);

        processor.overdue(taskId, now);

        verify(notifications).notifyAll(anyCollection(), eq(NotificationType.TASK_OVERDUE), any(), any(), any());
        verify(notifications).notify(any(User.class), eq(NotificationType.TASK_OVERDUE), any(), any(), any());
    }

    @Test
    void overdueIsNotSentTwice() {
        taskDueIn(Duration.ofHours(-5), TaskStatus.TODO);
        when(tasks.claimOverdue(taskId)).thenReturn(0);

        processor.overdue(taskId, now);

        verifyNoInteractions(notifications);
    }
}
