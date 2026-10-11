package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.UserCreateRequest;
import com.sevenb.user_manager.dto.UserPermissionOverrideDto;
import com.sevenb.user_manager.dto.UserPermissionOverrideRequest;
import com.sevenb.user_manager.dto.UserUpdateRequest;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.entity.UserPermissionOverride;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.repository.UserPermissionOverrideRepository;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import com.sevenb.user_manager.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceSelfProtectionTest {

    private static final String TENANT = "tenant-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final RoleService roleService = mock(RoleService.class);
    private final UserPermissionOverrideRepository overrideRepository = mock(UserPermissionOverrideRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final PermissionGuard permissionGuard = mock(PermissionGuard.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserService service = new UserService(userRepository, personRepository,
            mock(PasswordEncoder.class), userMapper, roleService, overrideRepository, permissionService,
            currentUserService, permissionGuard);

    private static UserPermissionOverride storedOverride(String code, boolean granted) {
        UserPermissionOverride override = new UserPermissionOverride();
        override.setPermissionCode(code);
        override.setGranted(granted);
        return override;
    }

    private void storedOverrides(long userId, UserPermissionOverride... overrides) {
        when(overrideRepository.findByUserId(userId)).thenReturn(List.of(overrides));
    }

    private void platformAdmin(boolean value) {
        when(permissionGuard.isPlatformAdmin()).thenReturn(value);
    }

    @Test
    void nonPlatformAdminCannotUpdatePasswordOfPlatformUser() {
        user(13L, "centauro", role(3L, "CAJERO"));
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", true));
        platformAdmin(false);
        currentUserIsAdmin(true);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setPassword("nueva");

        assertThatThrownBy(() -> service.updateUser(13L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador de la plataforma");
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonPlatformAdminCannotDeletePlatformUser() {
        user(13L, "centauro", role(3L, "CAJERO"));
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", true));
        platformAdmin(false);

        assertThatThrownBy(() -> service.deleteUser(13L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador de la plataforma");
        verify(userRepository, never()).delete(any());
    }

    @Test
    void nonPlatformAdminCannotChangeRoleOfPlatformUser() {
        user(13L, "centauro", role(3L, "CAJERO"));
        role(2L, "SUPERVISOR");
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", true));
        platformAdmin(false);

        assertThatThrownBy(() -> service.assignRole(13L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador de la plataforma");
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonPlatformAdminCannotChangeOverridesOfPlatformUser() {
        user(13L, "centauro", null);
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", true));
        platformAdmin(false);

        assertThatThrownBy(() -> service.setOverrides(13L, overrides("SALES_VIEW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrador de la plataforma");
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void platformAdminCanManagePlatformUser() {
        UserEntity target = user(13L, "centauro", role(3L, "CAJERO"));
        role(2L, "SUPERVISOR");
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", true));
        platformAdmin(true);

        service.assignRole(13L, 2L);

        verify(userRepository).save(target);
    }

    @Test
    void revokedPlatformOverrideDoesNotProtectUser() {
        UserEntity target = user(13L, "centauro", role(3L, "CAJERO"));
        role(2L, "SUPERVISOR");
        storedOverrides(13L, storedOverride("PLATFORM_ADMIN", false));
        platformAdmin(false);

        service.assignRole(13L, 2L);

        verify(userRepository).save(target);
    }

    @Test
    void getOverridesHidesPlatformCodesFromNonPlatformAdmin() {
        user(11L, "pedro", null);
        storedOverrides(11L, storedOverride("SALES_VIEW", true), storedOverride("PLATFORM_ADMIN", false));
        platformAdmin(false);
        when(userMapper.toOverrideDto(any())).thenAnswer(inv -> {
            UserPermissionOverride o = inv.getArgument(0);
            UserPermissionOverrideDto dto = new UserPermissionOverrideDto();
            dto.setPermissionCode(o.getPermissionCode());
            dto.setGranted(o.isGranted());
            return dto;
        });

        assertThat(service.getOverrides(11L)).extracting(UserPermissionOverrideDto::getPermissionCode)
                .containsExactly("SALES_VIEW");
    }

    @Test
    void getOverridesShowsPlatformCodesToPlatformAdmin() {
        user(11L, "pedro", null);
        storedOverrides(11L, storedOverride("SALES_VIEW", true), storedOverride("PLATFORM_ADMIN", false));
        platformAdmin(true);
        when(userMapper.toOverrideDto(any())).thenAnswer(inv -> {
            UserPermissionOverride o = inv.getArgument(0);
            UserPermissionOverrideDto dto = new UserPermissionOverrideDto();
            dto.setPermissionCode(o.getPermissionCode());
            return dto;
        });

        assertThat(service.getOverrides(11L)).extracting(UserPermissionOverrideDto::getPermissionCode)
                .containsExactlyInAnyOrder("SALES_VIEW", "PLATFORM_ADMIN");
    }

    @Test
    void setOverridesByNonPlatformAdminPreservesExistingPlatformOverrides() {
        user(11L, "pedro", null);
        UserPermissionOverride platform = storedOverride("PLATFORM_ADMIN", false);
        storedOverrides(11L, platform);
        when(overrideRepository.findByUserIdAndPermissionCode(11L, "PLATFORM_ADMIN")).thenReturn(Optional.of(platform));
        platformAdmin(false);

        service.setOverrides(11L, overrides("SALES_VIEW"));

        ArgumentCaptor<UserPermissionOverride> saved = ArgumentCaptor.forClass(UserPermissionOverride.class);
        verify(overrideRepository).save(saved.capture());
        assertThat(saved.getValue().getPermissionCode()).isEqualTo("SALES_VIEW");
        verify(overrideRepository, never()).delete(any());
        verify(overrideRepository, never()).deleteByUserIdAndPermissionCode(any(), any());
        assertThat(platform.isGranted()).isFalse();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, 1L, 1L, "juan", "SUPERVISOR", "RI");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UserEntity user(long id, String username, RoleEntity role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        when(userRepository.findByIdAndPerson_TenantId(id, TENANT)).thenReturn(Optional.of(user));
        return user;
    }

    private RoleEntity role(long id, String name) {
        RoleEntity role = new RoleEntity(id, TENANT, name, null, null);
        when(roleService.getRoleForTenant(id, TENANT)).thenReturn(role);
        return role;
    }

    private void currentUserIsAdmin(boolean admin) {
        when(currentUserService.isCurrentUserAdmin()).thenReturn(admin);
    }

    private void adminsInTenant(long count) {
        when(userRepository.countByPerson_TenantIdAndRole_NameIgnoreCase(TENANT, "ADMIN")).thenReturn(count);
    }

    private static UserPermissionOverrideRequest overrides(String code) {
        UserPermissionOverrideRequest.OverrideEntry entry = new UserPermissionOverrideRequest.OverrideEntry();
        entry.setPermissionCode(code);
        entry.setGranted(true);
        UserPermissionOverrideRequest request = new UserPermissionOverrideRequest();
        request.setOverrides(List.of(entry));
        return request;
    }

    @Test
    void cannotChangeOwnRole() {
        user(10L, "juan", role(2L, "SUPERVISOR"));
        role(1L, "ADMIN");

        assertThatThrownBy(() -> service.assignRole(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propio rol");
        verify(userRepository, never()).save(any());
    }

    @Test
    void canChangeAnotherUsersRole() {
        UserEntity other = user(11L, "pedro", role(3L, "CAJERO"));
        role(2L, "SUPERVISOR");

        service.assignRole(11L, 2L);

        verify(userRepository).save(other);
    }

    @Test
    void cannotChangeOwnOverrides() {
        user(10L, "juan", null);

        assertThatThrownBy(() -> service.setOverrides(10L, overrides("USERS_MANAGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propios permisos");
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void overridesWithUnknownCodeAreRejected() {
        user(11L, "pedro", null);
        when(permissionService.requireExistingCodes(any()))
                .thenThrow(new IllegalArgumentException("Códigos de permiso inexistentes: NOPE"));

        assertThatThrownBy(() -> service.setOverrides(11L, overrides("NOPE")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void nonPlatformAdminCannotGrantPlatformOverride() {
        user(11L, "pedro", null);
        when(permissionGuard.isPlatformAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.setOverrides(11L, overrides("PLATFORM_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
        verify(overrideRepository, never()).save(any());
    }

    @Test
    void platformAdminCanGrantPlatformOverride() {
        user(11L, "pedro", null);
        when(permissionGuard.isPlatformAdmin()).thenReturn(true);

        service.setOverrides(11L, overrides("PLATFORM_ADMIN"));

        verify(overrideRepository).save(any());
    }

    @Test
    void nonPlatformAdminCanStillGrantRegularOverride() {
        user(11L, "pedro", null);
        when(permissionGuard.isPlatformAdmin()).thenReturn(false);

        service.setOverrides(11L, overrides("SALES_VIEW"));

        verify(overrideRepository).save(any());
    }

    @Test
    void nonAdminCannotAssignAdminRole() {
        user(11L, "pedro", role(3L, "CAJERO"));
        role(1L, "ADMIN");
        currentUserIsAdmin(false);

        assertThatThrownBy(() -> service.assignRole(11L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asignar el rol ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminCanAssignAdminRole() {
        UserEntity other = user(11L, "pedro", role(3L, "CAJERO"));
        role(1L, "ADMIN");
        currentUserIsAdmin(true);

        service.assignRole(11L, 1L);

        verify(userRepository).save(other);
    }

    @Test
    void nonAdminCannotAssignAdminRoleOnUpdate() {
        user(11L, "pedro", role(3L, "CAJERO"));
        role(1L, "ADMIN");
        currentUserIsAdmin(false);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setRoleId(1L);

        assertThatThrownBy(() -> service.updateUser(11L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asignar el rol ADMIN");
    }

    @Test
    void nonAdminCannotCreateUserWithAdminRole() {
        Person person = new Person();
        when(personRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(person));
        role(1L, "ADMIN");
        currentUserIsAdmin(false);
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("nuevo");
        request.setPassword("secreta");
        request.setRoleId(1L);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asignar el rol ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonAdminCannotEditAdminUser() {
        user(12L, "jefe", role(1L, "ADMIN"));
        currentUserIsAdmin(false);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setPassword("nueva");

        assertThatThrownBy(() -> service.updateUser(12L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonAdminCannotChangeRoleOfAdminUser() {
        user(12L, "jefe", role(1L, "ADMIN"));
        role(3L, "CAJERO");
        currentUserIsAdmin(false);

        assertThatThrownBy(() -> service.assignRole(12L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario ADMIN");
    }

    @Test
    void nonAdminCannotDeleteAdminUser() {
        user(12L, "jefe", role(1L, "ADMIN"));
        currentUserIsAdmin(false);
        adminsInTenant(2);

        assertThatThrownBy(() -> service.deleteUser(12L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario ADMIN");
        verify(userRepository, never()).delete(any());
    }

    @Test
    void cannotDeleteSelf() {
        UserEntity me = user(10L, "juan", role(2L, "SUPERVISOR"));
        when(currentUserService.isCurrentUser(me)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUser(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tu propio usuario");
        verify(userRepository, never()).delete(any());
    }

    @Test
    void cannotDeleteLastAdmin() {
        user(12L, "jefe", role(1L, "ADMIN"));
        currentUserIsAdmin(true);
        adminsInTenant(1);

        assertThatThrownBy(() -> service.deleteUser(12L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("último usuario ADMIN");
        verify(userRepository, never()).delete(any());
    }

    @Test
    void adminCanDeleteAnotherAdminWhenNotLast() {
        UserEntity other = user(12L, "jefe", role(1L, "ADMIN"));
        currentUserIsAdmin(true);
        adminsInTenant(2);

        service.deleteUser(12L);

        verify(userRepository).delete(other);
    }

    @Test
    void cannotRemoveAdminRoleFromLastAdmin() {
        user(12L, "jefe", role(1L, "ADMIN"));
        role(3L, "CAJERO");
        currentUserIsAdmin(true);
        adminsInTenant(1);

        assertThatThrownBy(() -> service.assignRole(12L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("último usuario ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void canRemoveAdminRoleWhenAnotherAdminExists() {
        UserEntity other = user(12L, "jefe", role(1L, "ADMIN"));
        role(3L, "CAJERO");
        currentUserIsAdmin(true);
        adminsInTenant(2);

        service.assignRole(12L, 3L);

        verify(userRepository).save(other);
    }
}
