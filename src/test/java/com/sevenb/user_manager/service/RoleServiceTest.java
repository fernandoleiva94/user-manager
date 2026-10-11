package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.repository.RoleRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import com.sevenb.user_manager.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTest {

    private static final String TENANT = "tenant-1";

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final PermissionGuard permissionGuard = mock(PermissionGuard.class);
    private final RoleService service = new RoleService(roleRepository, permissionService, currentUserService,
            permissionGuard);

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, 1L, 1L, "juan", "ADMIN", "RI");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Set<PermissionEntity> permissions(String... codes) {
        Set<PermissionEntity> set = new HashSet<>();
        for (String code : codes) {
            PermissionEntity p = new PermissionEntity();
            p.setCode(code);
            set.add(p);
        }
        return set;
    }

    private RoleEntity role(long id, String name, String... codes) {
        RoleEntity role = new RoleEntity(id, TENANT, name, null, permissions(codes));
        when(roleRepository.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.of(role));
        return role;
    }

    private void stubExistingCodes() {
        when(permissionService.requireExistingCodes(any())).thenAnswer(inv -> {
            Set<String> codes = new HashSet<>(inv.getArgument(0));
            return permissions(codes.toArray(String[]::new));
        });
    }

    @Test
    void cannotDeleteAdminRole() {
        role(1L, "Admin");

        assertThatThrownBy(() -> service.deleteRole(1L)).isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void canDeleteOtherRole() {
        RoleEntity cajero = role(2L, "CAJERO");

        service.deleteRole(2L);

        verify(roleRepository).delete(cajero);
    }

    @Test
    void cannotRemovePermissionsFromAdmin() {
        role(1L, "ADMIN", "USERS_MANAGE", "ROLES_MANAGE", "SALES_VIEW");
        stubExistingCodes();

        assertThatThrownBy(() -> service.setPermissions(1L, Set.of("USERS_MANAGE", "ROLES_MANAGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SALES_VIEW");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void adminMustKeepUsersAndRolesManage() {
        role(1L, "ADMIN", "SALES_VIEW");
        stubExistingCodes();

        assertThatThrownBy(() -> service.setPermissions(1L, Set.of("SALES_VIEW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLES_MANAGE")
                .hasMessageContaining("USERS_MANAGE");
    }

    @Test
    void canAddPermissionsToAdmin() {
        RoleEntity admin = role(1L, "ADMIN", "USERS_MANAGE", "ROLES_MANAGE");
        stubExistingCodes();
        when(roleRepository.save(admin)).thenReturn(admin);

        service.setPermissions(1L, Set.of("USERS_MANAGE", "ROLES_MANAGE", "STOCK_VIEW"));

        verify(roleRepository).save(admin);
    }

    @Test
    void setPermissionsPropagatesUnknownCodeRejection() {
        role(2L, "CAJERO");
        when(permissionService.requireExistingCodes(any()))
                .thenThrow(new IllegalArgumentException("Códigos de permiso inexistentes: NOPE"));

        assertThatThrownBy(() -> service.setPermissions(2L, Set.of("NOPE")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    private void currentUserWithRole(RoleEntity role) {
        UserEntity current = new UserEntity();
        current.setUsername("juan");
        current.setRole(role);
        when(currentUserService.currentUser()).thenReturn(Optional.of(current));
    }

    @Test
    void nonAdminCannotChangeOwnRolePermissions() {
        RoleEntity supervisor = role(3L, "SUPERVISOR", "SALES_VIEW");
        currentUserWithRole(supervisor);
        stubExistingCodes();

        assertThatThrownBy(() -> service.setPermissions(3L, Set.of("SALES_VIEW", "USERS_MANAGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tu propio rol");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void nonAdminCanChangeOtherRolePermissions() {
        currentUserWithRole(new RoleEntity(3L, TENANT, "SUPERVISOR", null, permissions()));
        RoleEntity cajero = role(2L, "CAJERO", "SALES_VIEW");
        stubExistingCodes();
        when(roleRepository.save(cajero)).thenReturn(cajero);

        service.setPermissions(2L, Set.of("SALES_VIEW", "SALES_CREATE"));

        verify(roleRepository).save(cajero);
    }

    @Test
    void adminCanChangeOwnRolePermissions() {
        RoleEntity admin = role(1L, "ADMIN", "USERS_MANAGE", "ROLES_MANAGE");
        currentUserWithRole(admin);
        stubExistingCodes();
        when(roleRepository.save(admin)).thenReturn(admin);

        service.setPermissions(1L, Set.of("USERS_MANAGE", "ROLES_MANAGE", "STOCK_VIEW"));

        verify(roleRepository).save(admin);
    }

    @Test
    void setPermissionsRejectsPlatformCodes() {
        role(2L, "CAJERO");
        stubExistingCodes();

        assertThatThrownBy(() -> service.setPermissions(2L, Set.of("SALES_VIEW", "PLATFORM_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void setPermissionsRejectsPlatformCodesOnAdminRole() {
        role(1L, "ADMIN", "USERS_MANAGE", "ROLES_MANAGE");
        stubExistingCodes();

        assertThatThrownBy(() -> service.setPermissions(1L, Set.of("USERS_MANAGE", "ROLES_MANAGE", "PLATFORM_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void adminRoleCanDropPlatformCodesFromBackfill() {
        RoleEntity admin = role(1L, "ADMIN", "USERS_MANAGE", "ROLES_MANAGE", "PLATFORM_ADMIN");
        stubExistingCodes();
        when(roleRepository.save(admin)).thenReturn(admin);

        service.setPermissions(1L, Set.of("USERS_MANAGE", "ROLES_MANAGE"));

        verify(roleRepository).save(admin);
        assertThat(admin.getPermissions()).extracting(PermissionEntity::getCode).doesNotContain("PLATFORM_ADMIN");
    }

    @Test
    void createRoleRejectsPlatformCodes() {
        RoleEntity role = new RoleEntity(null, null, "VENDEDOR", null, permissions("SALES_VIEW", "PLATFORM_ADMIN"));
        stubExistingCodes();

        assertThatThrownBy(() -> service.createRole(role))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedDefaultRolesExcludesPlatformCodes() {
        when(roleRepository.existsByTenantId(TENANT)).thenReturn(false);
        when(permissionService.findByCodes(any())).thenReturn(permissions());

        service.seedDefaultRolesForTenant(TENANT);

        ArgumentCaptor<Set<String>> codes = ArgumentCaptor.forClass(Set.class);
        verify(permissionService, times(3)).findByCodes(codes.capture());
        assertThat(codes.getAllValues()).allSatisfy(set ->
                assertThat(set).noneMatch(code -> code.startsWith("PLATFORM_")));
        assertThat(codes.getAllValues().get(0)).contains("USERS_MANAGE", "ROLES_MANAGE");
    }

    @Test
    void getPermissionsHidesPlatformCodesFromNonPlatformAdmin() {
        RoleEntity admin = role(1L, "ADMIN", "USERS_MANAGE", "PLATFORM_ADMIN");
        when(permissionGuard.isPlatformAdmin()).thenReturn(false);

        assertThat(service.getPermissions(1L)).extracting(PermissionEntity::getCode).containsExactly("USERS_MANAGE");
        assertThat(admin.getPermissions()).hasSize(2);
    }

    @Test
    void getPermissionsShowsPlatformCodesToPlatformAdmin() {
        role(1L, "ADMIN", "USERS_MANAGE", "PLATFORM_ADMIN");
        when(permissionGuard.isPlatformAdmin()).thenReturn(true);

        assertThat(service.getPermissions(1L)).extracting(PermissionEntity::getCode)
                .containsExactlyInAnyOrder("USERS_MANAGE", "PLATFORM_ADMIN");
    }

    @Test
    void cannotCreateRoleNamedAdmin() {
        RoleEntity role = new RoleEntity();
        role.setName("admin");

        assertThatThrownBy(() -> service.createRole(role)).isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRoleNeverOverwritesExistingId() {
        RoleEntity role = new RoleEntity(1L, null, "VENDEDOR", null, null);
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoleEntity saved = service.createRole(role);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
    }
}
