package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    List<AppUser> findByAirlineId(UUID airlineId);
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<AppUser> findBySitesId(UUID siteId);

    /** Revoca las sesiones de todos los usuarios con MFA habilitado (reinicio de política). */
    @Modifying
    @Query("update AppUser u set u.tokensValidFrom = :validFrom where u.mfaEnabled = true")
    int revokeSessionsForMfaUsers(OffsetDateTime validFrom);
}
