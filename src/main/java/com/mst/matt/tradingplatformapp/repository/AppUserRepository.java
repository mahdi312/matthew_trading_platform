package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findAllByOrderByCreatedAtDesc();
    boolean existsByUsername(String username);
}
