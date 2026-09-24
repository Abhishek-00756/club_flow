package com.clubflow.dashboard;

import com.clubflow.event.EventDtos.EventDto;
import com.clubflow.task.TaskDtos.TaskDto;
import com.clubflow.task.TaskStatus;
import com.clubflow.user.UserRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record MemberDashboard(long activeTasks, long dueToday, long completed, long overdue, long awaitingReview,
                                  List<TaskDto> upcomingTasks, List<EventDto> upcomingEvents) {}

    /** One row of "who is late on what". A task with several assignees produces several rows. */
    public record OverdueRow(UUID taskId, String title, UserRef assignee, long hoursOverdue) {}

    public record ReviewRow(UUID taskId, String title, List<UserRef> assignees, Instant submittedAt) {}

    public record DepartmentStat(UUID id, String name, long active, long completed, long overdue) {}

    public record MemberStat(UUID userId, String name, long open, long inReview, long overdue, long completed) {}

    public record WeekPoint(LocalDate weekStart, long created, long completed) {}

    public record ClubDashboard(UUID clubId, String clubName, long members, long activeTasks, long completed,
                                long overdue, long awaitingReview, long upcomingEvents,
                                Map<TaskStatus, Long> byStatus, List<OverdueRow> overdueTasks,
                                List<ReviewRow> awaitingReviewTasks, List<DepartmentStat> departments,
                                List<MemberStat> workload, List<WeekPoint> weekly) {}

    public record ClubStat(UUID id, String name, long members, long activeTasks, long completed, long overdue) {}

    public record SuperAdminDashboard(long clubs, long users, long activeTasks, long completedTasks,
                                      long overdueTasks, long emailsSent, long emailsPending, long emailsFailed,
                                      List<ClubStat> clubStats) {}
}
