package com.clubflow.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringTaskTest {

    private RecurringTask weekly(int dayOfWeek) {
        RecurringTask r = new RecurringTask();
        r.setFrequency(Frequency.WEEKLY);
        r.setDayOfWeek(dayOfWeek);
        return r;
    }

    private RecurringTask monthly(int dayOfMonth) {
        RecurringTask r = new RecurringTask();
        r.setFrequency(Frequency.MONTHLY);
        r.setDayOfMonth(dayOfMonth);
        return r;
    }

    @Test
    void dailyRunsEveryDay() {
        RecurringTask r = new RecurringTask();
        r.setFrequency(Frequency.DAILY);
        assertTrue(r.isDueOn(LocalDate.of(2026, 9, 21)));
        assertTrue(r.isDueOn(LocalDate.of(2026, 9, 22)));
    }

    @Test
    void weeklyRunsOnlyOnItsWeekday() {
        // 21 Sep 2026 is a Monday (ISO day 1).
        assertTrue(weekly(1).isDueOn(LocalDate.of(2026, 9, 21)));
        assertFalse(weekly(1).isDueOn(LocalDate.of(2026, 9, 22)));
        assertTrue(weekly(7).isDueOn(LocalDate.of(2026, 9, 27)));
    }

    @Test
    void monthlyRunsOnItsDay() {
        assertTrue(monthly(15).isDueOn(LocalDate.of(2026, 9, 15)));
        assertFalse(monthly(15).isDueOn(LocalDate.of(2026, 9, 16)));
    }

    @Test
    void monthlyOnThe31stFallsBackToTheLastDayOfShortMonths() {
        assertTrue(monthly(31).isDueOn(LocalDate.of(2026, 2, 28)));
        assertFalse(monthly(31).isDueOn(LocalDate.of(2026, 2, 27)));
        assertTrue(monthly(31).isDueOn(LocalDate.of(2026, 4, 30)));
        assertTrue(monthly(31).isDueOn(LocalDate.of(2026, 5, 31)));
    }

    @Test
    void aScheduleMissingItsDayNeverRuns() {
        RecurringTask r = new RecurringTask();
        r.setFrequency(Frequency.WEEKLY);
        assertFalse(r.isDueOn(LocalDate.of(2026, 9, 21)));
    }
}
