package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.UserPermissionOverrideRequest;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.repository.UserPermissionOverrideRepository;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceSelfProtectionTest {

    private static final String TENANT = "tenant-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleService roleService = mock(RoleService.class);
    private final UserPermissionOverrideRepository overrideRepository = mock(UserPermissionOverrideRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final UserService service = new UserService(userRepository, mock(PersonRepository.class),
            mock(PasswordEncoder.class), mock(UserMapper.class), roleService, overrideRepository, permissionService);

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
}
