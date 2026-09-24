package com.clubflow.dashboard;

import com.clubflow.dashboard.DashboardDtos.ClubDashboard;
import com.clubflow.dashboard.DashboardDtos.MemberDashboard;
import com.clubflow.dashboard.DashboardDtos.SuperAdminDashboard;
import com.clubflow.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Three dashboards, chosen by what a person is allowed to see rather than by role name:
 * personal (anyone), club overview (REPORT_VIEW: secretary and admin) and system overview (CLUB_MANAGE).
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/member")
    @PreAuthorize("hasAnyAuthority('TASK_VIEW_ASSIGNED','TASK_VIEW_ALL')")
    public MemberDashboard member(@AuthenticationPrincipal AuthUser me) {
        return service.member(me);
    }

    @GetMapping("/club")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ClubDashboard club(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID clubId) {
        return service.club(me, clubId);
    }

    @GetMapping("/superadmin")
    @PreAuthorize("hasAuthority('CLUB_MANAGE')")
    public SuperAdminDashboard superAdmin() {
        return service.superAdmin();
    }
}
