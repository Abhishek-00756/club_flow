package com.clubflow.automation;

import com.clubflow.config.AppProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks every ten minutes for schedules that have not produced today's task yet. Running often
 * (rather than once at midnight) means a restart or a short outage does not skip a day.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskScheduler {
    private final RecurringTaskRepository repo;
    private final RecurringTaskService service;
    private final AppProperties props;

    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    public void run() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        for (UUID id : repo.findIdsNotRunOn(today)) {
            try {
                service.runIfDue(id, today);
            } catch (Exception ex) {
                log.warn("Recurring task {} failed", id, ex);
            }
        }
    }
}
