package com.clubflow.security;

import com.clubflow.audit.AuditService;
import com.clubflow.club.Club;
import com.clubflow.club.ClubRepository;
import com.clubflow.common.ApiException;
import com.clubflow.config.AppProperties;
import com.clubflow.security.AuthDtos.AuthResponse;
import com.clubflow.security.AuthDtos.SessionDto;
import com.clubflow.user.PermissionService;
import com.clubflow.user.Role;
import com.clubflow.user.User;
import com.clubflow.user.UserDto;
import com.clubflow.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final ClubRepository clubs;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PermissionService permissions;
    private final AppProperties props;
    private final AuditService audit;

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(AuthDtos.LoginRequest req, String userAgent, String ip) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            audit.logAs(user == null || user.getClub() == null ? null : user.getClub().getId(),
                    user == null ? null : user.getId(), email, "LOGIN_FAILED", "USER",
                    user == null ? null : user.getId(), null, null);
            throw ApiException.unauthorized("Email or password is incorrect.");
        }
        assertCanSignIn(user);
        user.setLastLoginAt(Instant.now());
        audit.logAs(clubIdOf(user), user.getId(), user.getName(), "LOGIN", "USER", user.getId(), null, null);
        return issue(user, userAgent, ip);
    }

    @Transactional
    public AuthResponse register(AuthDtos.RegisterRequest req, String userAgent, String ip) {
        Club club = clubs.findByJoinCodeIgnoreCase(req.clubCode().trim())
                .filter(Club::isActive)
                .orElseThrow(() -> ApiException.badRequest("That club code is not valid."));
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Someone already uses this email address.");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.MEMBER);
        user.setClub(club);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.logAs(club.getId(), user.getId(), user.getName(), "USER_REGISTERED", "USER", user.getId(), null, email);
        return issue(user, userAgent, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken, String userAgent, String ip) {
        RefreshToken token = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Your session has expired. Sign in again."));
        if (token.isRevoked()) {
            // A rotated token was presented again: assume it leaked and end every session for this user.
            refreshTokens.revokeAllForUser(token.getUser().getId());
            throw ApiException.unauthorized("Your session has expired. Sign in again.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Your session has expired. Sign in again.");
        }
        User user = token.getUser();
        assertCanSignIn(user);
        token.setRevoked(true);
        return issue(user, userAgent, ip);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void changePassword(AuthUser me, AuthDtos.ChangePasswordRequest req) {
        User user = users.findById(me.id()).orElseThrow(() -> ApiException.notFound("User not found."));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setUpdatedAt(Instant.now());
        refreshTokens.revokeAllForUser(user.getId());
        audit.log(me, clubIdOf(user), "PASSWORD_CHANGED", "USER", user.getId(), null, null);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> sessions(AuthUser me) {
        return refreshTokens
                .findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(me.id(), Instant.now()).stream()
                .map(t -> new SessionDto(t.getId(), t.getCreatedAt(), t.getExpiresAt(), t.getUserAgent(), t.getIpAddress()))
                .toList();
    }

    @Transactional
    public void revokeSession(AuthUser me, UUID sessionId) {
        RefreshToken token = refreshTokens.findById(sessionId)
                .filter(t -> t.getUser().getId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        token.setRevoked(true);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    // ---- internals ----

    private void assertCanSignIn(User user) {
        if (!user.isActive()) {
            throw ApiException.forbidden("This account is deactivated. Ask your club admin to reactivate it.");
        }
        if (user.getClub() != null && !user.getClub().isActive()) {
            throw ApiException.forbidden("This club is currently inactive.");
        }
    }

    private AuthResponse issue(User user, String userAgent, String ip) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(props.jwt().refreshTokenDays())));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 250)));
        token.setIpAddress(ip);
        refreshTokens.save(token);

        UserDto dto = UserDto.from(user, permissions.permissionsFor(user.getRole()));
        return new AuthResponse(jwt.createAccessToken(user), raw, jwt.accessTtlSeconds(), dto);
    }

    private UUID clubIdOf(User user) {
        return user.getClub() == null ? null : user.getClub().getId();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
