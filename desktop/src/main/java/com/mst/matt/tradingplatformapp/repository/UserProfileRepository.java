package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link UserProfile}.
 *
 * <p>This repository is transitional: it will be removed once the desktop app
 * fully delegates profile storage to {@code identity-service}.  Methods that
 * accept an {@link AppUser} parameter are deprecated in favour of the
 * {@code findByAppUserIdOrderByLastAccessedAtDesc} equivalents so that
 * controllers that have already switched to the thin-client {@code AuthService}
 * (which returns a {@code SessionUser} with only a userId, not an
 * {@link AppUser} entity) can still query profiles without re-fetching the
 * full {@link AppUser} from the database.</p>
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByActive(boolean active);

    List<UserProfile> findAllByOrderByLastAccessedAtDesc();

    Optional<UserProfile> findByName(String name);

    // ── Legacy AppUser-based queries (still used by un-migrated services) ────

    /** @deprecated Use {@link #findByAppUserIdOrderByLastAccessedAtDesc(Long)} instead. */
    @Deprecated(forRemoval = true)
    List<UserProfile> findByAppUserOrderByLastAccessedAtDesc(AppUser appUser);

    List<UserProfile> findByAppUserIsNullOrderByLastAccessedAtDesc();

    /** @deprecated Use {@link #findByAppUserIdAndName(Long, String)} instead. */
    @Deprecated(forRemoval = true)
    Optional<UserProfile> findByAppUserAndName(AppUser appUser, String name);

    boolean existsByName(String name);

    // ── userId-based queries — used by thin-client-migrated controllers ───────

    /**
     * Returns all profiles owned by the user with the given id, most recently
     * accessed first.  Does not require loading the {@link AppUser} entity.
     */
    @Query("SELECT p FROM UserProfile p WHERE p.appUser.id = :userId ORDER BY p.lastAccessedAt DESC")
    List<UserProfile> findByAppUserIdOrderByLastAccessedAtDesc(@Param("userId") Long userId);

    /**
     * Returns a profile owned by the given user id with the given name.
     */
    @Query("SELECT p FROM UserProfile p WHERE p.appUser.id = :userId AND p.name = :name")
    Optional<UserProfile> findByAppUserIdAndName(@Param("userId") Long userId,
                                                 @Param("name")   String name);
}
