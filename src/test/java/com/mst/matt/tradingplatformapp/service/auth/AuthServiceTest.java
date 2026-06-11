package com.mst.matt.tradingplatformapp.service.auth;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.AppUser.Role;
import com.mst.matt.tradingplatformapp.model.RolePermission;
import com.mst.matt.tradingplatformapp.repository.AppUserRepository;
import com.mst.matt.tradingplatformapp.repository.RolePermissionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 * Uses Mockito stubs for the two repositories.
 */
class AuthServiceTest {

    private AppUserRepository       userRepo;
    private RolePermissionRepository permRepo;
    private AuthService             svc;

    // ── Fixtures ───────────────────────────────────────────────

    private AppUser adminUser() {
        AppUser u = AppUser.builder()
                .id(1L).username("admin").displayName("Admin")
                .role(Role.ADMIN).active(true).build();
        u.setPassword("admin123");
        return u;
    }

    private AppUser regularUser() {
        AppUser u = AppUser.builder()
                .id(2L).username("alice").displayName("Alice")
                .role(Role.REGULAR_USER).active(true).build();
        u.setPassword("pass123");
        return u;
    }

    private AppUser proUser() {
        AppUser u = AppUser.builder()
                .id(3L).username("bob").displayName("Bob")
                .role(Role.PRO_USER).active(true).build();
        u.setPassword("pass456");
        return u;
    }

    @BeforeEach
    void setUp() {
        userRepo = mock(AppUserRepository.class);
        permRepo = mock(RolePermissionRepository.class);

        // Bootstrap: one existing admin → ensureAdminExists() does nothing
        AppUser admin = adminUser();
        when(userRepo.count()).thenReturn(1L);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permRepo.findBySubjectUserIdAndTabName(any(), any())).thenReturn(Optional.empty());
        when(permRepo.findBySubjectRoleAndTabName(any(), any())).thenReturn(Optional.empty());

        svc = new AuthService(userRepo, permRepo);
    }

    // ── bootstrap ─────────────────────────────────────────────

    @Test
    void bootstrapCreatesAdminWhenNoUsersExist() {
        AppUserRepository emptyRepo = mock(AppUserRepository.class);
        when(emptyRepo.count()).thenReturn(0L);
        when(emptyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permRepo.findBySubjectUserIdAndTabName(any(), any())).thenReturn(Optional.empty());
        when(permRepo.findBySubjectRoleAndTabName(any(), any())).thenReturn(Optional.empty());

        new AuthService(emptyRepo, permRepo);

        verify(emptyRepo, atLeastOnce()).save(argThat(u ->
                "admin".equals(u.getUsername()) && u.getRole() == Role.ADMIN));
    }

    // ── login ──────────────────────────────────────────────────

    @Test
    void login_success_returnsUserAndSetsCurrentUser() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));

        Optional<AppUser> result = svc.login("admin", "admin123");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
        assertThat(svc.isLoggedIn()).isTrue();
        assertThat(svc.isAdmin()).isTrue();
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));

        Optional<AppUser> result = svc.login("admin", "wrongpassword");

        assertThat(result).isEmpty();
        assertThat(svc.isLoggedIn()).isFalse();
    }

    @Test
    void login_unknownUser_returnsEmpty() {
        when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());

        Optional<AppUser> result = svc.login("nobody", "pass");

        assertThat(result).isEmpty();
    }

    @Test
    void login_inactiveUser_returnsEmpty() {
        AppUser inactive = regularUser();
        inactive.setActive(false);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(inactive));

        Optional<AppUser> result = svc.login("alice", "pass123");

        assertThat(result).isEmpty();
    }

    @Test
    void login_normalizesCaseAndTrims() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));

        Optional<AppUser> result = svc.login("  ADMIN  ", "admin123");

        assertThat(result).isPresent();
    }

    // ── logout ─────────────────────────────────────────────────

    @Test
    void logout_clearsCurrentUser() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        svc.logout();

        assertThat(svc.isLoggedIn()).isFalse();
        assertThat(svc.currentUser()).isEmpty();
    }

    // ── register ───────────────────────────────────────────────

    @Test
    void register_createsRegularUser() {
        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser u = svc.register("newuser", "pass123", "New User", Role.REGULAR_USER);

        assertThat(u.getUsername()).isEqualTo("newuser");
        assertThat(u.getRole()).isEqualTo(Role.REGULAR_USER);
    }

    @Test
    void register_duplicateUsername_throws() {
        when(userRepo.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> svc.register("admin", "pass", "Dup", Role.REGULAR_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void register_nonAdminCannotCreateAdminRole() {
        // Not logged in — should throw
        when(userRepo.existsByUsername("hacker")).thenReturn(false);

        assertThatThrownBy(() -> svc.register("hacker", "pass", "H", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_adminCanCreateAnyRole() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        when(userRepo.existsByUsername("newpro")).thenReturn(false);
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppUser pro = svc.register("newpro", "pass123", "New Pro", Role.PRO_USER);
        assertThat(pro.getRole()).isEqualTo(Role.PRO_USER);
    }

    // ── canSeeTab ──────────────────────────────────────────────

    @Test
    void canSeeTab_notLoggedIn_returnsFalse() {
        assertThat(svc.canSeeTab("CHART")).isFalse();
    }

    @Test
    void canSeeTab_admin_seesEverything() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        for (String tab : AuthService.ALL_TABS) {
            assertThat(svc.canSeeTab(tab)).isTrue();
        }
    }

    @Test
    void canSeeTab_perUserDbOverride_returnsOverride() {
        AppUser user = regularUser();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        // Per-user override: CHART hidden
        RolePermission override = RolePermission.builder()
                .subjectUserId(2L).tabName("CHART").visible(false).build();
        when(permRepo.findBySubjectUserIdAndTabName(2L, "CHART"))
                .thenReturn(Optional.of(override));

        assertThat(svc.canSeeTab("CHART")).isFalse();
    }

    @Test
    void canSeeTab_hiddenTabsField_hidesTab() {
        AppUser user = regularUser();
        user.setHiddenTabs("EXPORT,FUNDAMENTALS");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        assertThat(svc.canSeeTab("EXPORT")).isFalse();
        assertThat(svc.canSeeTab("FUNDAMENTALS")).isFalse();
        assertThat(svc.canSeeTab("CHART")).isTrue();
    }

    // ── canUseTimeframe ────────────────────────────────────────

    @Test
    void canUseTimeframe_regularUser_onlyAllowedFrames() {
        AppUser user = regularUser();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        assertThat(svc.canUseTimeframe("1h")).isTrue();
        assertThat(svc.canUseTimeframe("4h")).isTrue();
        assertThat(svc.canUseTimeframe("1d")).isTrue();
        assertThat(svc.canUseTimeframe("1m")).isFalse();
        assertThat(svc.canUseTimeframe("3m")).isFalse();
    }

    @Test
    void canUseTimeframe_proUser_intraday() {
        AppUser user = proUser();
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(user));
        svc.login("bob", "pass456");

        assertThat(svc.canUseTimeframe("1m")).isTrue();
        assertThat(svc.canUseTimeframe("15m")).isTrue();
        assertThat(svc.canUseTimeframe("1h")).isTrue();
        // PRO_USER does NOT have 2h, 6h, 8h, 12h
        assertThat(svc.canUseTimeframe("2h")).isFalse();
    }

    @Test
    void canUseTimeframe_admin_allFrames() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        List<String> all = List.of("1m","3m","5m","15m","30m","1h","2h","4h","6h","8h","12h","1d","3d","1w","1mo");
        for (String tf : all) {
            assertThat(svc.canUseTimeframe(tf))
                    .as("Admin should be able to use %s", tf)
                    .isTrue();
        }
    }

    // ── maxCandles ─────────────────────────────────────────────

    @Test
    void maxCandles_notLoggedIn_returns200() {
        assertThat(svc.maxCandles()).isEqualTo(200);
    }

    @Test
    void maxCandles_perRole() {
        assertThat(Role.REGULAR_USER.maxCandles()).isEqualTo(200);
        assertThat(Role.PRO_USER.maxCandles()).isEqualTo(1000);
        assertThat(Role.PRO_PLUS_USER.maxCandles()).isEqualTo(2000);
        assertThat(Role.ADMIN.maxCandles()).isEqualTo(5000);
    }

    // ── favorites ─────────────────────────────────────────────

    @Test
    void getFavoriteTimeframes_empty_whenNotLoggedIn() {
        assertThat(svc.getFavoriteTimeframes()).isEmpty();
    }

    @Test
    void saveFavoriteTimeframes_persistsToUser() {
        AppUser user = regularUser();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        svc.saveFavoriteTimeframes(List.of("1h", "4h", "1d"));

        verify(userRepo, atLeastOnce()).save(argThat(u ->
                "1h,4h,1d".equals(u.getFavoriteTimeframes())));
    }

    @Test
    void getFavoriteTimeframes_filtersToAllowed() {
        AppUser user = regularUser();
        user.setFavoriteTimeframes("1h,4h,1m"); // 1m NOT allowed for REGULAR_USER
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        List<String> favs = svc.getFavoriteTimeframes();
        assertThat(favs).contains("1h", "4h").doesNotContain("1m");
    }

    // ── admin CRUD ─────────────────────────────────────────────

    @Test
    void changeRole_requiresAdmin() {
        AppUser user = regularUser();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        assertThatThrownBy(() -> svc.changeRole(3L, Role.ADMIN))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void deleteUser_adminCanDelete() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        svc.deleteUser(99L);

        verify(userRepo).deleteById(99L);
        verify(permRepo).deleteBySubjectUserId(99L);
    }

    @Test
    void deleteUser_cannotDeleteSelf() {
        AppUser admin = adminUser();
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        svc.login("admin", "admin123");

        assertThatThrownBy(() -> svc.deleteUser(1L))  // admin.id == 1
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void allUsers_requiresAdmin() {
        AppUser user = regularUser();
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        svc.login("alice", "pass123");

        assertThatThrownBy(() -> svc.allUsers())
                .isInstanceOf(SecurityException.class);
    }
}
