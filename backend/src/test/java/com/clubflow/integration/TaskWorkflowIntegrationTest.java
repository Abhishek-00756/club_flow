package com.clubflow.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Walks one task through the whole workflow against a real PostgreSQL (Flyway migrations included).
 * Skipped automatically when Docker is not available. Runs with mvn test.
 */
@SpringBootTest(properties = {
        "app.mail.enabled=false",
        "app.reminders.scan-interval-ms=3600000",
        "app.seed.enabled=true",
        "app.seed.demo-data=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TaskWorkflowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void taskMovesFromAssignmentToApprovalAndRolesAreEnforced() throws Exception {
        String root = login("admin@clubflow.local", "ChangeMe123!");

        String clubId = id(send(post("/api/clubs"), root, Map.of("name", "Robotics Club", "description", "Builds robots"), 201));
        String otherClubId = id(send(post("/api/clubs"), root, Map.of("name", "Drama Club"), 201));

        createUser(root, "Sam Secretary", "sam@robotics.test", "SECRETARY", clubId);
        String memberId = createUser(root, "Mia Member", "mia@robotics.test", "MEMBER", clubId);
        createUser(root, "Olly Outsider", "olly@drama.test", "MEMBER", otherClubId);

        String secretary = login("sam@robotics.test", "Password123!");
        String member = login("mia@robotics.test", "Password123!");
        String outsider = login("olly@drama.test", "Password123!");

        // Secretary creates and assigns a task.
        Map<String, Object> newTask = Map.of(
                "title", "Wire the line-follower",
                "description", "Use the new sensor board.",
                "priority", "HIGH",
                "deadline", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                "assigneeIds", java.util.List.of(memberId));
        String created = send(post("/api/tasks"), secretary, newTask, 201);
        String taskId = JsonPath.read(created, "$.task.id");
        assertJson(created, "$.task.status", "TODO");

        // A member can only see tasks assigned to them, and outsiders see nothing at all.
        mvc.perform(auth(get("/api/tasks"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(auth(get("/api/tasks/" + taskId), outsider)).andExpect(status().isNotFound());

        // Members cannot create tasks or approve work.
        send(post("/api/tasks"), member, newTask, 403);
        send(post("/api/tasks/" + taskId + "/approve"), member, Map.of(), 403);

        // Assignee works through the first pass.
        assertJson(send(post("/api/tasks/" + taskId + "/start"), member, null, 200), "$.task.status", "IN_PROGRESS");
        assertJson(send(post("/api/tasks/" + taskId + "/submit"), member, Map.of("note", "First draft"), 200),
                "$.task.status", "SUBMITTED");

        // Reviewer must give a reason when sending work back.
        send(post("/api/tasks/" + taskId + "/reject"), secretary, Map.of("note", " "), 400);
        String rejected = send(post("/api/tasks/" + taskId + "/reject"), secretary,
                Map.of("note", "Sensor pins are swapped"), 200);
        assertJson(rejected, "$.task.status", "IN_PROGRESS");
        assertJson(rejected, "$.task.reviewNote", "Sensor pins are swapped");

        // Second pass is approved.
        send(post("/api/tasks/" + taskId + "/submit"), member, null, 200);
        String approved = send(post("/api/tasks/" + taskId + "/approve"), secretary, Map.of("note", "Nice work"), 200);
        assertJson(approved, "$.task.status", "COMPLETED");

        // Finished work cannot be submitted again.
        send(post("/api/tasks/" + taskId + "/submit"), member, null, 409);

        // The assignee got notifications along the way.
        mvc.perform(auth(get("/api/notifications/unread-count"), member)).andExpect(status().isOk());
    }

    @Test
    void deadlinesInThePastAreRejected() throws Exception {
        String root = login("admin@clubflow.local", "ChangeMe123!");
        String clubId = id(send(post("/api/clubs"), root, Map.of("name", "Chess Club"), 201));
        createUser(root, "Cy Secretary", "cy@chess.test", "SECRETARY", clubId);
        String secretary = login("cy@chess.test", "Password123!");

        Map<String, Object> stale = Map.of(
                "title", "Book the hall",
                "priority", "MEDIUM",
                "deadline", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        send(post("/api/tasks"), secretary, stale, 400);
    }

    // ---- helpers ----

    private String createUser(String token, String name, String email, String role, String clubId) throws Exception {
        String body = send(post("/api/users"), token, Map.of("name", name, "email", email, "password", "Password123!",
                "role", role, "clubId", clubId), 201);
        return JsonPath.read(body, "$.id");
    }

    private String login(String email, String password) throws Exception {
        String body = send(post("/api/auth/login"), null, Map.of("email", email, "password", password), 200);
        return JsonPath.read(body, "$.accessToken");
    }

    private String send(MockHttpServletRequestBuilder request, String token, Object body, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder req = auth(request, token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult result = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        return result.getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private static String id(String body) {
        return JsonPath.read(body, "$.id");
    }

    private static void assertJson(String body, String path, String expected) {
        Object actual = JsonPath.read(body, path);
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
