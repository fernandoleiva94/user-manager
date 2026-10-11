package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.entity.UserPermissionOverride;
import com.sevenb.user_manager.repository.UserPermissionOverrideRepository;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionResolutionServiceTest {

    private static final String TENANT = "tenant-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPermissionOverrideRepository overrideRepository = mock(UserPermissionOverrideRepository.class);
    private final PermissionResolutionService service =
            new PermissionResolutionService(userRepository, overrideRepository);

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, 1L, 1L, "juan", "ADMIN", "RI");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void userWithRole(long id, String... codes) {
        Set<PermissionEntity> permissions = new HashSet<>();
        for (String code : codes) {
            PermissionEntity p = new PermissionEntity();
            p.setCode(code);
            permissions.add(p);
        }
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(new RoleEntity(1L, TENANT, "ADMIN", null, permissions));
        when(userRepository.findByIdAndPerson_TenantId(id, TENANT)).thenReturn(Optional.of(user));
    }

    private static UserPermissionOverride override(String code, boolean granted) {
        UserPermissionOverride override = new UserPermissionOverride();
        override.setPermissionCode(code);
        override.setGranted(granted);
        return override;
    }

    @Test
    void platformCodesFromRoleAreIgnored() {
        userWithRole(10L, "USERS_MANAGE", "PLATFORM_ADMIN");
        when(overrideRepository.findByUserId(10L)).thenReturn(List.of());

        assertThat(service.resolvePermissions(10L)).containsExactly("USERS_MANAGE");
    }

    @Test
    void platformCodesFromGrantedOverrideAreKept() {
        userWithRole(10L, "USERS_MANAGE");
        when(overrideRepository.findByUserId(10L)).thenReturn(List.of(override("PLATFORM_ADMIN", true)));

        assertThat(service.resolvePermissions(10L)).containsExactlyInAnyOrder("USERS_MANAGE", "PLATFORM_ADMIN");
    }
}
