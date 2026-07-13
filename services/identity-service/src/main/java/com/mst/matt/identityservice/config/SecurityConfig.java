package com.mst.matt.identityservice.config;

import com.mst.matt.identityservice.security.JwtAuthFilter;
import com.mst.matt.identityservice.security.OAuth2AuthenticationSuccessHandler;
import com.mst.matt.identityservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for {@code identity-service}.
 *
 * <h3>Authentication paths</h3>
 * <ol>
 *   <li><b>Username/password</b> — {@code POST /auth/login} handled by
 *       {@link com.mst.matt.identityservice.controller.AuthController};
 *       credentials verified via {@link DaoAuthenticationProvider} +
 *       {@link UserService#loadUserByUsername}.</li>
 *   <li><b>Google OAuth2</b> — Spring Security's built-in OAuth2 Client flow
 *       triggered at {@code /oauth2/authorization/google};
 *       success handled by {@link OAuth2AuthenticationSuccessHandler} which
 *       issues a platform JWT and redirects to the frontend.</li>
 * </ol>
 *
 * <h3>Stateless JWT validation</h3>
 * <p>{@link JwtAuthFilter} runs before every request and populates the
 * {@code SecurityContextHolder} from a valid Bearer token.  The same filter
 * logic is duplicated in {@code gateway-service} so that JWT is checked once,
 * at the edge, for all downstream services.</p>
 *
 * <h3>Session policy</h3>
 * <p>STATELESS — no HttpSession is created or used for API endpoints.
 * OAuth2 temporarily uses the session during the redirect handshake; the
 * session is discarded once the JWT is issued.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final UserService userService;

    // ── Public endpoints ──────────────────────────────────────────────────────

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/register",
            "/auth/oauth2/**",          // OAuth2 callback redirect target
            "/oauth2/**",               // Spring Security OAuth2 authorization endpoints
            "/actuator/health",
            "/actuator/info"
    };

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for stateless JWT API (re-enable if serving browser forms)
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — JWTs carry all state
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )

                // OAuth2 Client — Google login
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                )

                // Register JWT filter before the standard username/password filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    // ── Authentication provider (DAO — for username/password path) ────────────

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // ── Password encoding ─────────────────────────────────────────────────────

    /**
     * BCrypt encoder — cost factor 12 provides good security/performance balance.
     * Used both here and injected into {@link UserService} indirectly via
     * Spring Security's {@link DaoAuthenticationProvider}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
