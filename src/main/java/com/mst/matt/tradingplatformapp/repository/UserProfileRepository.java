package com.mst.matt.tradingplatformapp.repository;

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
}
