package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * P1 (LOG-FIX): async writer for {@link UserProfile} so JavaFX UI threads never
 * call {@code repo.save(...)} synchronously — that pattern was the root cause of
 * the {@code [SQLITE_BUSY] The database file is locked} errors observed when
 * users clicked {@code onChartProviderChanged}.
 *
 * <p>Combined with {@code journal_mode=WAL} and {@code hikari.maximum-pool-size=1},
 * profile writes now happen on the {@code SimpleAsyncTaskExecutor} thread pool,
 * serialised behind the single writer connection.
 */
@Service
public class ProfilePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePersistenceService.class);

    private final UserProfileRepository repository;

    public ProfilePersistenceService(UserProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist the profile off the calling thread and return a future for callers
     * that care about completion. The JavaFX UI thread should fire-and-forget
     * (ignore the returned future); background services that need the saved
     * entity can {@code .join()} or chain on the future.
     */
    @Async
    @Transactional
    public CompletableFuture<UserProfile> saveAsync(UserProfile profile) {
        try {
            UserProfile saved = repository.save(profile);
            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            log.warn("Async profile save failed for id={} name={}: {}",
                    profile != null ? profile.getId() : null,
                    profile != null ? profile.getName() : null,
                    e.getMessage());
            CompletableFuture<UserProfile> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}
