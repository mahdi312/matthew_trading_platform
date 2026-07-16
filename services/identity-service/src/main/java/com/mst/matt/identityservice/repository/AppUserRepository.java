package com.mst.matt.identityservice.repository;

import com.mst.matt.identityservice.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link AppUser}.
 *
 * <p>Intentionally thin — business logic lives in
 * {@link com.mst.matt.identityservice.service.UserService}, not here.</p>
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    /**
     * Look up an OAuth2 account by provider + provider-specific subject id.
     *
     * @param authProvider the OAuth2 provider (e.g., GOOGLE)
     * @param providerSubject the provider's unique subject identifier ("sub" claim)
     */
    Optional<AppUser> findByAuthProviderAndProviderSubject(
            AppUser.AuthProvider authProvider, String providerSubject);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
