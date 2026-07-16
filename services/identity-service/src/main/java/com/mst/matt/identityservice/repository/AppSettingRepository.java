package com.mst.matt.identityservice.repository;

import com.mst.matt.identityservice.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for per-user {@link AppSetting} key-value pairs.
 */
@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    /** Find a single setting by owner and key. */
    Optional<AppSetting> findByAppUserIdAndKey(Long appUserId, String key);

    /** All settings for a given user. */
    List<AppSetting> findByAppUserId(Long appUserId);

    /** Delete a setting by owner and key. */
    void deleteByAppUserIdAndKey(Long appUserId, String key);
}
