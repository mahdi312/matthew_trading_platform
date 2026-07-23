package com.mst.matt.identityservice.service;

import com.mst.matt.identityservice.model.UserProfile;
import com.mst.matt.identityservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Async writer for {@link UserProfile} so REST threads are never blocked by JPA commits.
 *
 * <p>Ported from the desktop monolith's {@code ProfilePersistenceService}; the async
 * pattern is retained here even though the REST environment doesn't have the SQLite WAL
 * concern — it keeps the write path non-blocking and makes it easy to add a cache layer
 * or event publication later without touching callers.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePersistenceService {

    private final UserProfileRepository repository;

    /**
     * Persist the profile off the calling thread and return a future for callers
     * that care about completion.  REST handlers that just want fire-and-forget
     * can ignore the returned future.
     */
    @Async
    @Transactional
    public CompletableFuture<UserProfile> saveAsync(UserProfile profile) {
        try {
            UserProfile saved = repository.save(profile);
            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            log.warn("Async profile save failed for id={} name={}: {}",
                    profile != null ? profile.getId()   : null,
                    profile != null ? profile.getName() : null,
                    e.getMessage());
            CompletableFuture<UserProfile> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}
