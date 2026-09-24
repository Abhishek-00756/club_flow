package com.clubflow.security;

import com.clubflow.user.Permission;
import com.clubflow.user.PermissionService;
import com.clubflow.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the bearer token, then loads the user so deactivated accounts and role changes take
 * effect immediately instead of waiting for the token to expire.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final PermissionService permissions;

    public JwtAuthFilter(JwtService jwt, UserRepository users, PermissionService permissions) {
        this.jwt = jwt;
        this.users = users;
        this.permissions = permissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parseUserId(header.substring(7).trim())
                    .flatMap(users::findById)
                    .filter(u -> u.isActive() && (u.getClub() == null || u.getClub().isActive()))
                    .ifPresent(u -> {
                        Set<Permission> perms = permissions.permissionsFor(u.getRole());
                        AuthUser principal = new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                                u.getClub() == null ? null : u.getClub().getId(), perms);
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
                        perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
                        SecurityContextHolder.getContext()
                                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
                    });
        }
        chain.doFilter(request, response);
    }
}
