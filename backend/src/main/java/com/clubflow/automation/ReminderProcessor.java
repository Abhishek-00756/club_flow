package com.clubflow.automation;

import com.clubflow.common.Formats;
import com.clubflow.config.AppProperties;
import com.clubflow.notification.NotificationService;
import com.clubflow.notification.NotificationType;
import com.clubflow.task.Task;
import com.clubflow.task.TaskRepository;
import com.clubflow.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles one task at a time, each in its own transaction, so a problem with one task never blocks
 * the rest. The "claim" queries make every reminder fire exactly once even with several instances.
 */
@Service
@RequiredArgsConstructor
public class ReminderProcessor {
    private final TaskRepository tasks;
    private final NotificationService notifications;
    private final AppProperties props;

    /** Sends the "due tomorrow" or "due in a few hours" reminder, whichever fits the time left. */
    @Transactional
    public void remind(UUID taskId, Instant now) {
        Task task = tasks.findById(taskId).orElse(null);
        if (task == null || !task.getStatus().isOpen() || !task.getDeadline().isAfter(now)) {
            return;
        }
        Duration left = Duration.between(now, task.getDeadline());
        boolean urgent = left.compareTo(Duration.ofHours(props.reminders().secondHours())) <= 0;

        // Claiming before loading the recipients keeps a concurrent scan from sending a duplicate.
        int claimed = urgent ? tasks.claimSecondReminder(taskId) : tasks.claimFirstReminder(taskId);
        if (claimed == 0) {
            return;
        }
        String due = Formats.dateTime(task.getDeadline(), props.timezone());
        String title = urgent ? "Due in " + humanize(left) + ": " + task.getTitle() : "Due soon: " + task.getTitle();
        String assigneeMessage = urgent
                ? "\"" + task.getTitle() + "\" is due in " + humanize(left) + " (" + due + ")."
                : "\"" + task.getTitle() + "\" is due " + due + ".";

        List<User> assignees = task.assigneeUsers();
        if (assignees.isEmpty()) {
            notifications.notify(task.getCreatedBy(), NotificationType.TASK_REMINDER, title,
                    "\"" + task.getTitle() + "\" is due " + due + " and nobody is assigned to it yet.", link(task));
        } else {
            notifications.notifyAll(assignees, NotificationType.TASK_REMINDER, title, assigneeMessage, link(task));
        }
    }

    /** Deadline has passed and the work is not submitted: tell the assignees and whoever set the task. */
    @Transactional
    public void overdue(UUID taskId, Instant now) {
        Task task = tasks.findById(taskId).orElse(null);
        if (task == null || !task.getStatus().isOpen() || task.getDeadline().isAfter(now)) {
            return;
        }
        if (tasks.claimOverdue(taskId) == 0) {
            return;
        }
        String due = Formats.dateTime(task.getDeadline(), props.timezone());
        List<User> assignees = task.assigneeUsers();
        notifications.notifyAll(assignees, NotificationType.TASK_OVERDUE, "Overdue: " + task.getTitle(),
                "The deadline for \"" + task.getTitle() + "\" passed on " + due + ". Please submit it or ask for more time.",
                link(task));

        String who = assignees.isEmpty() ? "Nobody was assigned, and it"
                : assignees.stream().map(User::getName).collect(Collectors.joining(", ")) + " did not complete it. It";
        notifications.notify(task.getCreatedBy(), NotificationType.TASK_OVERDUE, "Missed deadline: " + task.getTitle(),
                "\"" + task.getTitle() + "\" was due " + due + ". " + who + " is now overdue.", link(task));
    }

    private static String humanize(Duration d) {
        long minutes = Math.max(d.toMinutes(), 1);
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = Math.round(minutes / 60.0);
        return hours + (hours == 1 ? " hour" : " hours");
    }

    private static String link(Task t) {
        return "/tasks/" + t.getId();
    }
}
