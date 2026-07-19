package com.mst.matt.identityservice.config;

import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a local admin account exists for development / first-run login.
 *
 * <pre>
 *   username: admin
 *   password: admin1234
 * </pre>
 *
 * Idempotent — skips insert when {@code admin} already exists.
 * Change the password in production immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevAdminUserSeedRunner implements ApplicationRunner {

    public static final String DEV_ADMIN_USERNAME = "admin";
    public static final String DEV_ADMIN_PASSWORD = "admin1234";

    private final AppUserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(DEV_ADMIN_USERNAME)) {
            return;
        }

        AppUser admin = AppUser.builder()
                .username(DEV_ADMIN_USERNAME)
                .displayName("Administrator")
                .email("admin@localhost")
                .authProvider(AppUser.AuthProvider.LOCAL)
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
        admin.setPassword(DEV_ADMIN_PASSWORD);

        userRepository.save(admin);
        log.warn(
                "Seeded local admin user '{}' (password: {}). Change this password outside local dev.",
                DEV_ADMIN_USERNAME,
                DEV_ADMIN_PASSWORD
        );
    }
}
