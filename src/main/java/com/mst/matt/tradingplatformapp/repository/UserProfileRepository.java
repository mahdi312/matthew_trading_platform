package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByActive(boolean active);
    List<UserProfile> findAllByOrderByLastAccessedAtDesc();
    Optional<UserProfile> findByName(String name);

    /** Returns all profiles owned by the given AppUser, most recently used first. */
    List<UserProfile> findByAppUserOrderByLastAccessedAtDesc(AppUser appUser);

    /** Returns profiles where appUser is null (legacy/unowned) or owned by this user. */
    List<UserProfile> findByAppUserIsNullOrderByLastAccessedAtDesc();

    /** Finds a profile owned by the given user with the given name (for duplicate-prevention). */
    Optional<UserProfile> findByAppUserAndName(AppUser appUser, String name);

    /** Finds a profile with the given name regardless of owner (for global uniqueness check). */
    boolean existsByName(String name);
}
