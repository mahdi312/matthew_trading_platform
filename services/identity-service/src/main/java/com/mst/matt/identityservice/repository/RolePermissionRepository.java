package com.mst.matt.identityservice.repository;

import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link RolePermission}.
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /**
     * All permissions for a specific role (role-wide rules).
     */
    List<RolePermission> findBySubjectRole(AppUser.Role subjectRole);

    /**
     * All permissions for a specific user (per-user overrides).
     */
    List<RolePermission> findBySubjectUserId(Long userId);

    /**
     * Combined: all rules that apply to this user (either directly or via role).
     */
    List<RolePermission> findBySubjectRoleOrSubjectUserId(AppUser.Role role, Long userId);
}
