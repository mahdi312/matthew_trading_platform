package com.mst.matt.identityservice.service;

import com.mst.matt.identityservice.model.UserProfile;
import com.mst.matt.identityservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup migration that patches legacy {@link UserProfile} rows whose
 * {@code assetFocus}, {@code chartProvider}, or {@code fundamentalProvider}
 * columns were persisted as {@code NULL} before the {@code nullable = false}
 * schema change.
 *
 * <p>Runs once on every boot; it is idempotent (skips rows that already have values)
 * and cheap (the user_profiles table has at most a handful of rows per user in practice).</p>
 *
 * <p>Ported from the desktop monolith's {@code ProfileMigrationRunner}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileMigrationRunner implements ApplicationRunner {

    private final UserProfileRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long patched = repository.findAll().stream()
                .filter(p -> p.getAssetFocus() == null
                        || p.getChartProvider() == null
                        || p.getFundamentalProvider() == null)
                .peek(p -> {
                    if (p.getAssetFocus() == null) {
                        p.setAssetFocus(UserProfile.ProfileAssetFocus.MULTI);
                    }
                    if (p.getChartProvider() == null) {
                        p.setChartProvider("AUTO");
                    }
                    if (p.getFundamentalProvider() == null) {
                        p.setFundamentalProvider("AUTO");
                    }
                    repository.save(p);
                })
                .count();

        if (patched > 0) {
            log.info("ProfileMigrationRunner: patched {} legacy profile row(s) with "
                    + "default focus/provider values.", patched);
        }
    }
}
