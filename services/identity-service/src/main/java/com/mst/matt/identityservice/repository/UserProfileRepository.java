package com.mst.matt.identityservice.repository;

import com.mst.matt.identityservice.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link UserProfile}.
 *
 * <p>Ported from the desktop monolith — cross-service FK queries (AppUser entity) are
 * replaced with plain {@code appUserId} Long queries so no entity dependency exists
 * between user_profiles and app_users at the JPA layer.</p>
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /** Find the currently active profile (should be at most one). */
    Optional<UserProfile> findByActive(boolean active);

    /** All profiles ordered by most recently accessed first. */
    List<UserProfile> findAllByOrderByLastAccessedAtDesc();

    /** Find a profile by its unique name. */
    Optional<UserProfile> findByName(String name);

    /** All profiles belonging to the given user, most recently used first. */
    List<UserProfile> findByAppUserIdOrderByLastAccessedAtDesc(Long appUserId);

    /** Profiles with no owner (legacy/unowned), most recently used first. */
    List<UserProfile> findByAppUserIdIsNullOrderByLastAccessedAtDesc();

    /**
     * Find a profile owned by the given user with the given name (duplicate prevention).
     */
    Optional<UserProfile> findByAppUserIdAndName(Long appUserId, String name);

    /** Check whether a profile name already exists (global uniqueness). */
    boolean existsByName(String name);
}
