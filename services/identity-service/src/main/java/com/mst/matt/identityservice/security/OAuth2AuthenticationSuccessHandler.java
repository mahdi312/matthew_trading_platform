package com.mst.matt.identityservice.security;

import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Invoked by Spring Security after a successful Google OAuth2 authentication.
 *
 * <p>Extracts the Google user info from the {@link OAuth2User}, delegates
 * account look-up/creation to {@link UserService}, issues a platform JWT, and
 * redirects the browser to the frontend with the token as a query parameter.</p>
 *
 * <p>In a production setup the redirect URI (and the JWT delivery mechanism)
 * should be configured externally; the current implementation uses a simple
 * redirect for clarity.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email          = oAuth2User.getAttribute("email");
        String name           = oAuth2User.getAttribute("name");
        String providerSubject = oAuth2User.getAttribute("sub");    // Google's unique user ID

        AppUser user = userService.findOrCreateGoogleUser(email, name, providerSubject);
        String  jwt  = jwtUtil.generateToken(user);

        log.info("OAuth2 login success for userId={}", user.getId());

        // Redirect to frontend with JWT in query param.
        // In production: use a cookie or a short-lived code exchange instead.
        String targetUrl = "/auth/oauth2/success?token=" + jwt;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
