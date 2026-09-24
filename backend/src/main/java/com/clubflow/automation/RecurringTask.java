package com.clubflow.automation;

import com.clubflow.club.Club;
import com.clubflow.club.Department;
import com.clubflow.task.Priority;
import com.clubflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A task that creates itself on a schedule: "every Monday: social media planning".
 * dayOfWeek is 1 (Monday) to 7 (Sunday); dayOfMonth is 1 to 31 (short months use their last day).
 */
@Entity
@Table(name = "recurring_tasks")
@Getter
@Setter
@NoArgsConstructor
public class RecurringTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /** Days between the task being created and its deadline. */
    @Column(name = "due_in_days", nullable = false)
    private int dueInDays = 3;

    @Column(name = "due_time", nullable = false)
    private LocalTime dueTime;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_run_on")
    private LocalDate lastRunOn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToMany
    @JoinTable(name = "recurring_task_assignees",
            joinColumns = @JoinColumn(name = "recurring_task_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> assignees = new LinkedHashSet<>();

    /** Whether this schedule produces a task on the given (club-local) date. */
    public boolean isDueOn(LocalDate date) {
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> dayOfWeek != null && date.getDayOfWeek().getValue() == dayOfWeek;
            case MONTHLY -> dayOfMonth != null
                    && date.getDayOfMonth() == Math.min(dayOfMonth, date.lengthOfMonth());
        };
    }
}
