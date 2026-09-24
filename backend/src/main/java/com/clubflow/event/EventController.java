package com.clubflow.event;

import com.clubflow.event.EventDtos.AddAttendeeRequest;
import com.clubflow.event.EventDtos.AttendanceRequest;
import com.clubflow.event.EventDtos.EventDetail;
import com.clubflow.event.EventDtos.EventDto;
import com.clubflow.event.EventDtos.EventRequest;
import com.clubflow.event.EventDtos.JoinRequest;
import com.clubflow.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService service;

    @GetMapping
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public List<EventDto> list(@AuthenticationPrincipal AuthUser me,
                               @RequestParam(required = false) UUID clubId,
                               @RequestParam(defaultValue = "false") boolean upcoming) {
        return service.list(me, clubId, upcoming);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public EventDetail get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    public EventDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EventRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    public EventDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody EventRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EVENT_DELETE')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public EventDto join(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                         @RequestBody(required = false) JoinRequest body) {
        return service.join(me, id, body == null ? null : body.participation());
    }

    @DeleteMapping("/{id}/join")
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public EventDto leave(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.leave(me, id);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public EventDto checkIn(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.checkIn(me, id);
    }

    @PostMapping("/{id}/attendees")
    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    public EventDetail addAttendee(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                   @Valid @RequestBody AddAttendeeRequest req) {
        return service.addAttendee(me, id, req);
    }

    @PutMapping("/{id}/attendees/{userId}/attendance")
    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    public EventDetail setAttendance(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                     @PathVariable UUID userId, @Valid @RequestBody AttendanceRequest req) {
        return service.setAttendance(me, id, userId, req.attended());
    }

    @DeleteMapping("/{id}/attendees/{userId}")
    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    public EventDetail removeAttendee(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                      @PathVariable UUID userId) {
        return service.removeAttendee(me, id, userId);
    }
}
