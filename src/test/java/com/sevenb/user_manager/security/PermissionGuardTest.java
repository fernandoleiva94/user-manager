package com.sevenb.user_manager.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGuardTest {

    private final PermissionGuard shadow = new PermissionGuard(false);
    private final PermissionGuard enforcing = new PermissionGuard(true);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role, List<String> permissions) {
        JwtService.JwtClaims claims = new JwtService.JwtClaims(
                "tenant-1", 1L, 1L, "juan", role, "RI", permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, List.of()));
    }

    @Test
    void adminBypassesEvenWithEmptyPermissions() {
        authenticate("admin", List.of());
        assertThat(enforcing.has("USERS_MANAGE")).isTrue();
        assertThat(enforcing.hasAny("ROLES_MANAGE", "USERS_MANAGE")).isTrue();
    }

    @Test
    void legacyTokenWithoutPermissionsClaimIsAllowed() {
        authenticate("CAJERO", null);
        assertThat(enforcing.has("USERS_MANAGE")).isTrue();
    }

    @Test
    void grantedPermissionIsAllowed() {
        authenticate("SUPERVISOR", List.of("ROLES_MANAGE"));
        assertThat(enforcing.hasAny("USERS_MANAGE", "ROLES_MANAGE")).isTrue();
    }

    @Test
    void emptyListDeniesWhenEnforcing() {
        authenticate("CAJERO", List.of());
        assertThat(enforcing.has("USERS_MANAGE")).isFalse();
    }

    @Test
    void missingPermissionDeniedWhenEnforcing() {
        authenticate("CAJERO", List.of("SALES_CREATE"));
        assertThat(enforcing.hasAny("USERS_MANAGE", "ROLES_MANAGE")).isFalse();
    }

    @Test
    void missingPermissionAllowedInShadowMode() {
        authenticate("CAJERO", List.of("SALES_CREATE"));
        assertThat(shadow.has("USERS_MANAGE")).isTrue();
    }

    @Test
    void emptyListAllowedInShadowMode() {
        authenticate("CAJERO", List.of());
        assertThat(shadow.has("USERS_MANAGE")).isTrue();
    }

    @Test
    void noAuthenticationIsDenied() {
        assertThat(shadow.has("USERS_MANAGE")).isFalse();
    }
}
