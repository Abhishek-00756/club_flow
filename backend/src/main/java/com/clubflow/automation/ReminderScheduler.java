package com.clubflow.automation;

import com.clubflow.config.AppProperties;
import com.clubflow.task.Task;
import com.clubflow.task.TaskRepository;
import com.clubflow.task.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks for tasks that need a nudge: due within the reminder window, or past their deadline.
 * It only finds candidates; {@link ReminderProcessor} does the work for each one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {
    private final TaskRepository tasks;
    private final ReminderProcessor processor;
    private final AppProperties props;

    @Scheduled(fixedDelayString = "${app.reminders.scan-interval-ms}", initialDelay = 20_000)
    public void scan() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofHours(props.reminders().firstHours()));

        for (Task t : tasks.findDueForReminder(TaskStatus.OPEN, now, windowEnd)) {
            try {
                processor.remind(t.getId(), now);
            } catch (Exception ex) {
                log.warn("Reminder failed for task {}", t.getId(), ex);
            }
        }
        for (Task t : tasks.findNewlyOverdue(TaskStatus.OPEN, now)) {
            try {
                processor.overdue(t.getId(), now);
            } catch (Exception ex) {
                log.warn("Overdue notice failed for task {}", t.getId(), ex);
            }
        }
    }
}
