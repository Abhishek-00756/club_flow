package com.clubflow.seed;

import com.clubflow.announcement.Announcement;
import com.clubflow.announcement.AnnouncementRepository;
import com.clubflow.automation.Frequency;
import com.clubflow.automation.RecurringTask;
import com.clubflow.automation.RecurringTaskRepository;
import com.clubflow.automation.TaskTemplate;
import com.clubflow.automation.TaskTemplateItem;
import com.clubflow.automation.TaskTemplateRepository;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.club.Department;
import com.clubflow.club.DepartmentRepository;
import com.clubflow.config.AppProperties;
import com.clubflow.event.Event;
import com.clubflow.event.EventAttendee;
import com.clubflow.event.EventAttendeeRepository;
import com.clubflow.event.EventRepository;
import com.clubflow.event.Participation;
import com.clubflow.notification.Notification;
import com.clubflow.notification.NotificationRepository;
import com.clubflow.notification.NotificationType;
import com.clubflow.task.Priority;
import com.clubflow.task.Task;
import com.clubflow.task.TaskAssignee;
import com.clubflow.task.TaskComment;
import com.clubflow.task.TaskCommentRepository;
import com.clubflow.task.TaskRepository;
import com.clubflow.task.TaskStatus;
import com.clubflow.user.Role;
import com.clubflow.user.User;
import com.clubflow.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedService {
    private static final String DEMO_PASSWORD = "Password123!";
    private static final String DEFAULT_SUPERADMIN_PASSWORD = "ChangeMe123!";

    private final UserRepository users;
    private final ClubRepository clubs;
    private final DepartmentRepository departments;
    private final EventRepository events;
    private final EventAttendeeRepository attendees;
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskTemplateRepository templates;
    private final RecurringTaskRepository recurring;
    private final AnnouncementRepository announcements;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Transactional
    public void createSuperAdminIfMissing() {
        AppProperties.Seed seed = props.seed();
        if (!seed.enabled() || !users.findByRole(Role.SUPERADMIN).isEmpty()) {
            return;
        }
        if (users.existsByEmailIgnoreCase(seed.superadminEmail())) {
            log.warn("Cannot create the first SuperAdmin: {} is already registered with another role.",
                    seed.superadminEmail());
            return;
        }
        User u = newUser(seed.superadminName(), seed.superadminEmail(), seed.superadminPassword(), Role.SUPERADMIN,
                null, null);
        users.save(u);
        log.info("Created the first SuperAdmin account: {}", u.getEmail());
        if (DEFAULT_SUPERADMIN_PASSWORD.equals(seed.superadminPassword())) {
            log.warn("The SuperAdmin is using the default password. Set SUPERADMIN_PASSWORD, or change it after signing in.");
        }
    }

    /** A small, believable club so every screen has something to show. Only runs on an empty database. */
    @Transactional
    public void createDemoDataIfRequested() {
        if (!props.seed().demoData() || clubs.count() > 0) {
            return;
        }
        Instant now = Instant.now();

        Club club = new Club();
        club.setName("Astronomy Club");
        club.setDescription("Stargazing nights, workshops and the annual Astrothon.");
        club.setJoinCode("ASTRO2026");
        clubs.save(club);
        Map<String, Department> dept = new HashMap<>();
        for (String name : List.of("Technical", "Design", "Marketing", "Content", "Events", "PR")) {
            Department d = new Department();
            d.setClub(club);
            d.setName(name);
            dept.put(name, departments.save(d));
        }

        User admin = users.save(newUser("Aarav Mehta", "admin@astroclub.example", DEMO_PASSWORD, Role.ADMIN, club, null));
        User secretary = users.save(newUser("Priya Nair", "secretary@astroclub.example", DEMO_PASSWORD, Role.SECRETARY, club,
                dept.get("Events")));
        User jointSec = users.save(newUser("Rohan Iyer", "jointsecretary@astroclub.example", DEMO_PASSWORD,
                Role.JOINT_SECRETARY, club, dept.get("Marketing")));
        User rahul = users.save(newUser("Rahul Sharma", "rahul@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("Design")));
        User aman = users.save(newUser("Aman Verma", "aman@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("Marketing")));
        User sneha = users.save(newUser("Sneha Kulkarni", "sneha@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("Technical")));
        User karan = users.save(newUser("Karan Patil", "karan@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("Content")));
        User ananya = users.save(newUser("Ananya Desai", "ananya@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("Events")));
        User vikram = users.save(newUser("Vikram Joshi", "vikram@astroclub.example", DEMO_PASSWORD, Role.MEMBER, club,
                dept.get("PR")));

        Event astrothon = new Event();
        astrothon.setClub(club);
        astrothon.setTitle("Astrothon 2026");
        astrothon.setDescription("A day-long astronomy fest: talks, telescope sessions and a sky-mapping contest.");
        astrothon.setLocation("College Auditorium");
        astrothon.setStartsAt(now.plus(Duration.ofDays(14)));
        astrothon.setEndsAt(now.plus(Duration.ofDays(14)).plus(Duration.ofHours(8)));
        astrothon.setCreatedBy(secretary);
        events.save(astrothon);
        for (User v : List.of(ananya, vikram, sneha)) {
            attend(astrothon, v, Participation.VOLUNTEER);
        }
        attend(astrothon, rahul, Participation.PARTICIPANT);

        // Tasks in every stage of the workflow.
        Task poster = task(club, astrothon, dept.get("Design"), secretary, "Design Astrothon Instagram post",
                "Square 1080x1080 poster with the date, venue and the registration QR code.", Priority.HIGH,
                TaskStatus.IN_PROGRESS, now.plus(Duration.ofDays(3)), now.minus(Duration.ofDays(2)), rahul);
        poster.setReviewNote("Please make the event date bigger and move the QR code to the bottom right.");
        task(club, astrothon, dept.get("Technical"), secretary, "Update the club website with Astrothon details",
                "Add the schedule, speakers and a link to the registration form.", Priority.MEDIUM, TaskStatus.TODO,
                now.plus(Duration.ofDays(5)), now.minus(Duration.ofDays(1)), sneha);
        Task sponsors = task(club, astrothon, dept.get("Marketing"), jointSec, "Sponsorship outreach",
                "Email the five local companies on the shortlist and log their replies.", Priority.URGENT,
                TaskStatus.TODO, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(9)), aman);
        Task copy = task(club, astrothon, dept.get("Content"), secretary, "Write the event announcement",
                "About 150 words, friendly tone, for the college notice board and Instagram.", Priority.MEDIUM,
                TaskStatus.SUBMITTED, now.plus(Duration.ofDays(2)), now.minus(Duration.ofDays(6)), karan);
        copy.setSubmittedAt(now.minus(Duration.ofHours(3)));
        task(club, astrothon, dept.get("Events"), secretary, "Book the auditorium", "Confirm the booking with the admin office.",
                Priority.HIGH, TaskStatus.COMPLETED, now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(20)), ananya)
                .setCompletedAt(now.minus(Duration.ofDays(4)));
        task(club, null, dept.get("PR"), secretary, "Contact the local newspaper", "Ask whether they can cover the event.",
                Priority.LOW, TaskStatus.COMPLETED, now.minus(Duration.ofDays(12)), now.minus(Duration.ofDays(30)), vikram)
                .setCompletedAt(now.minus(Duration.ofDays(13)));
        task(club, null, dept.get("Design"), jointSec, "Refresh the membership form", "Update the questions for the new semester.",
                Priority.LOW, TaskStatus.COMPLETED, now.minus(Duration.ofDays(20)), now.minus(Duration.ofDays(36)), rahul)
                .setCompletedAt(now.minus(Duration.ofDays(22)));
        task(club, null, dept.get("Content"), secretary, "Stargazing night recap post", "Short recap with three photos.",
                Priority.MEDIUM, TaskStatus.COMPLETED, now.minus(Duration.ofDays(27)), now.minus(Duration.ofDays(40)), karan)
                .setCompletedAt(now.minus(Duration.ofDays(29)));

        comment(poster, secretary, "Changes requested: Please make the event date bigger and move the QR code to the bottom right.");
        comment(poster, rahul, "On it. I will upload v2 tonight.");
        comment(copy, karan, "Draft attached in the shared doc. Let me know if the tone works.");

        TaskTemplate campaign = new TaskTemplate();
        campaign.setClub(club);
        campaign.setName("Event campaign");
        campaign.setDescription("The standard set of promotion and logistics tasks for a club event.");
        addItem(campaign, "Design the poster", Priority.HIGH, dept.get("Design"), 14);
        addItem(campaign, "Write the Instagram caption", Priority.MEDIUM, dept.get("Content"), 12);
        addItem(campaign, "Create the Instagram story", Priority.MEDIUM, dept.get("Design"), 10);
        addItem(campaign, "Publish the announcement post", Priority.HIGH, dept.get("Marketing"), 9);
        addItem(campaign, "Contact sponsors", Priority.HIGH, dept.get("PR"), 12);
        addItem(campaign, "Create the registration form", Priority.HIGH, dept.get("Technical"), 13);
        addItem(campaign, "Send the event announcement to all members", Priority.MEDIUM, dept.get("Events"), 7);
        templates.save(campaign);

        recurring.save(recurringTask(club, admin, "Weekly meeting report", "Summarise decisions and action items.",
                Frequency.WEEKLY, 5, null, 2, LocalTime.of(18, 0), dept.get("Content"), List.of(karan)));
        recurring.save(recurringTask(club, admin, "Social media planning", "Plan the week's posts and stories.",
                Frequency.WEEKLY, 1, null, 2, LocalTime.of(20, 0), dept.get("Marketing"), List.of(aman)));

        Announcement welcome = new Announcement();
        welcome.setClub(club);
        welcome.setAuthor(secretary);
        welcome.setTitle("Astrothon 2026 is on");
        welcome.setBody("Astrothon is in two weeks. Check your tasks, sign up as a volunteer on the event page, and ask "
                + "in the club chat if you are unsure what to pick up.");
        announcements.save(welcome);

        inAppNotice(rahul, NotificationType.TASK_REJECTED, "Changes requested: Design Astrothon Instagram post",
                "Priya Nair asked for changes: \"Please make the event date bigger and move the QR code to the bottom right.\"",
                "/tasks/" + poster.getId());
        inAppNotice(aman, NotificationType.TASK_OVERDUE, "Overdue: Sponsorship outreach",
                "The deadline for \"Sponsorship outreach\" has passed. Please submit it or ask for more time.",
                "/tasks/" + sponsors.getId());
        inAppNotice(secretary, NotificationType.TASK_SUBMITTED, "Submitted for review: Write the event announcement",
                "Karan Patil submitted this task for review.", "/tasks/" + copy.getId());

        log.info("Demo data created. Sign in as secretary@astroclub.example / {} (join code ASTRO2026).", DEMO_PASSWORD);
    }

    // ---- helpers ----

    private User newUser(String name, String email, String password, Role role, Club club, Department department) {
        User u = new User();
        u.setName(name);
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setClub(club);
        u.setDepartment(department);
        return u;
    }

    private Task task(Club club, Event event, Department dept, User creator, String title, String description,
                      Priority priority, TaskStatus status, Instant deadline, Instant createdAt, User... assignees) {
        Task t = new Task();
        t.setClub(club);
        t.setEvent(event);
        t.setDepartment(dept);
        t.setCreatedBy(creator);
        t.setTitle(title);
        t.setDescription(description);
        t.setPriority(priority);
        t.setStatus(status);
        t.setDeadline(deadline);
        t.setCreatedAt(createdAt);
        t.setUpdatedAt(createdAt);
        for (User u : assignees) {
            t.getAssignees().add(new TaskAssignee(t, u));
        }
        return tasks.save(t);
    }

    private void comment(Task task, User author, String body) {
        TaskComment c = new TaskComment();
        c.setTask(task);
        c.setAuthor(author);
        c.setBody(body);
        comments.save(c);
    }

    private void attend(Event event, User user, Participation participation) {
        EventAttendee a = new EventAttendee();
        a.setEvent(event);
        a.setUser(user);
        a.setParticipation(participation);
        attendees.save(a);
    }

    private void addItem(TaskTemplate t, String title, Priority priority, Department dept, int daysBefore) {
        TaskTemplateItem item = new TaskTemplateItem();
        item.setTemplate(t);
        item.setTitle(title);
        item.setPriority(priority);
        item.setDepartment(dept);
        item.setDaysBeforeEvent(daysBefore);
        item.setPosition(t.getItems().size());
        t.getItems().add(item);
    }

    private RecurringTask recurringTask(Club club, User creator, String title, String description, Frequency frequency,
                                        Integer dayOfWeek, Integer dayOfMonth, int dueInDays, LocalTime dueTime,
                                        Department dept, List<User> assignees) {
        RecurringTask r = new RecurringTask();
        r.setClub(club);
        r.setCreatedBy(creator);
        r.setTitle(title);
        r.setDescription(description);
        r.setPriority(Priority.MEDIUM);
        r.setFrequency(frequency);
        r.setDayOfWeek(dayOfWeek);
        r.setDayOfMonth(dayOfMonth);
        r.setDueInDays(dueInDays);
        r.setDueTime(dueTime);
        r.setDepartment(dept);
        r.getAssignees().addAll(assignees);
        return r;
    }

    private void inAppNotice(User to, NotificationType type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUser(to);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        notifications.save(n);
    }
}
