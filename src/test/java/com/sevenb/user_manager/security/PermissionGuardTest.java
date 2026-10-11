package com.sevenb.user_manager.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionGuard shadow = new PermissionGuard(false, userRepository);
    private final PermissionGuard enforcing = new PermissionGuard(true, userRepository);

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

    @Test
    void platformAdminRequiresPermissionsClaim() {
        authenticate("ADMIN", List.of("USERS_MANAGE", "PLATFORM_ADMIN"));
        assertThat(enforcing.isPlatformAdmin()).isTrue();
        assertThat(shadow.isPlatformAdmin()).isTrue();
    }

    @Test
    void adminRoleIsNotPlatformAdmin() {
        authenticate("ADMIN", List.of("USERS_MANAGE", "ROLES_MANAGE"));
        assertThat(enforcing.isPlatformAdmin()).isFalse();
    }

    @Test
    void legacyTokenIsNotPlatformAdmin() {
        authenticate("ADMIN", null);
        assertThat(enforcing.isPlatformAdmin()).isFalse();
    }

    @Test
    void shadowModeDoesNotGrantPlatformAdmin() {
        authenticate("CAJERO", List.of("SALES_CREATE"));
        assertThat(shadow.isPlatformAdmin()).isFalse();
    }

    @Test
    void noAuthenticationIsNotPlatformAdmin() {
        assertThat(shadow.isPlatformAdmin()).isFalse();
    }

    private void storedUser(long id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        when(userRepository.findByIdAndPerson_TenantId(id, "tenant-1")).thenReturn(Optional.of(user));
    }

    @Test
    void selfCanReadOwnResolvedPermissionsWithoutPermission() {
        authenticate("CAJERO", List.of());
        storedUser(7L, "juan");
        assertThat(enforcing.isSelfOrHasAny(7L, "USERS_MANAGE", "ROLES_MANAGE")).isTrue();
    }

    @Test
    void otherUserRequiresPermissionWhenEnforcing() {
        authenticate("CAJERO", List.of());
        storedUser(8L, "pedro");
        assertThat(enforcing.isSelfOrHasAny(8L, "USERS_MANAGE", "ROLES_MANAGE")).isFalse();
    }

    @Test
    void otherUserAllowedWithPermission() {
        authenticate("SUPERVISOR", List.of("ROLES_MANAGE"));
        storedUser(8L, "pedro");
        assertThat(enforcing.isSelfOrHasAny(8L, "USERS_MANAGE", "ROLES_MANAGE")).isTrue();
    }

    @Test
    void otherUserAllowedInShadowMode() {
        authenticate("CAJERO", List.of());
        storedUser(8L, "pedro");
        assertThat(shadow.isSelfOrHasAny(8L, "USERS_MANAGE", "ROLES_MANAGE")).isTrue();
    }

    @Test
    void userOfAnotherTenantIsNotSelf() {
        authenticate("CAJERO", List.of());
        when(userRepository.findByIdAndPerson_TenantId(9L, "tenant-1")).thenReturn(Optional.empty());
        assertThat(enforcing.isSelfOrHasAny(9L, "USERS_MANAGE")).isFalse();
    }
}
