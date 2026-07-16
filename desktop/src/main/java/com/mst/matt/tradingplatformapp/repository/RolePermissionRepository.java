package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findBySubjectRole(AppUser.Role role);
    List<RolePermission> findBySubjectUserId(Long userId);
    Optional<RolePermission> findBySubjectRoleAndTabName(AppUser.Role role, String tabName);
    Optional<RolePermission> findBySubjectUserIdAndTabName(Long userId, String tabName);
    void deleteBySubjectRole(AppUser.Role role);
    void deleteBySubjectUserId(Long userId);
}
