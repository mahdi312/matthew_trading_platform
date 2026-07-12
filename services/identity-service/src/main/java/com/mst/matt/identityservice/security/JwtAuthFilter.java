package com.mst.matt.identityservice.security;

import com.mst.matt.identityservice.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that intercepts every request, extracts the {@code Authorization: Bearer <token>}
 * header, validates the JWT using {@link JwtUtil}, and populates the
 * {@link SecurityContextHolder} so that Spring Security sees an authenticated principal.
 *
 * <p>This filter is registered in {@link com.mst.matt.identityservice.config.SecurityConfig}
 * <em>before</em> {@code UsernamePasswordAuthenticationFilter}.</p>
 *
 * <p>The same validation logic is used in {@code gateway-service}'s equivalent filter,
 * which performs JWT validation once at the edge so downstream services do not need
 * to repeat it.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.validateAndExtractClaims(token);
                String username = claims.getSubject();
                String role     = claims.get("role", String.class);

                // Verify the user still exists and is active in the DB
                userService.loadActiveUserByUsername(username).ifPresent(user -> {
                    var authority = new SimpleGrantedAuthority("ROLE_" + role);
                    var auth = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(authority));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });

            } catch (JwtException ex) {
                log.debug("Invalid JWT in request to {}: {}", request.getRequestURI(), ex.getMessage());
                // Do not set authentication — Spring Security will handle the 401
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract the raw JWT string from the {@code Authorization} header.
     *
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
