package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * P2 (LOG-FIX): startup migration that patches legacy {@link UserProfile} rows whose
 * {@code assetFocus} column was persisted as {@code NULL} before the
 * {@code nullable = false} schema change.
 *
 * <p>Without this, every controller calling {@code profile.getAssetFocus().defaultSymbol()}
 * would throw {@code NullPointerException} — see the log lines from
 * {@code YearlyProfitController.setProfile}, {@code ProfileSettingsController.setProfile},
 * and {@code ChartController.refreshProviderCombo}.
 *
 * <p>Runs once on every boot; it's idempotent (skips rows that already have a value)
 * and cheap (the user_profiles table has at most a handful of rows in practice).
 */
@Component
public class ProfileMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileMigrationRunner.class);

    private final UserProfileRepository repository;

    public ProfileMigrationRunner(UserProfileRepository repository) {
        this.repository = repository;
    }

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
            log.info("ProfileMigrationRunner: patched {} legacy profile row(s) with default focus/provider values.", patched);
        }
    }
}
