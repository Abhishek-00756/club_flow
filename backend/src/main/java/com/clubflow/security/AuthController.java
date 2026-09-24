package com.clubflow.security;

import com.clubflow.security.AuthDtos.AuthResponse;
import com.clubflow.security.AuthDtos.SessionDto;
import com.clubflow.user.UserDto;
import com.clubflow.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return users.me(me);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me,
                               @Valid @RequestBody AuthDtos.ChangePasswordRequest req) {
        auth.changePassword(me, req);
    }

    @GetMapping("/sessions")
    public List<SessionDto> sessions(@AuthenticationPrincipal AuthUser me) {
        return auth.sessions(me);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        auth.revokeSession(me, id);
    }
}
