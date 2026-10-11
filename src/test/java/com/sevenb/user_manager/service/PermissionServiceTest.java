package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.repository.PermissionRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    private final PermissionRepository repository = mock(PermissionRepository.class);
    private final PermissionGuard permissionGuard = mock(PermissionGuard.class);
    private final PermissionService service = new PermissionService(repository, permissionGuard);

    private static PermissionEntity permission(String code) {
        PermissionEntity p = new PermissionEntity();
        p.setCode(code);
        return p;
    }

    @Test
    void catalogContainsNewCodesAndSalesViewDescription() {
        assertThat(PermissionService.ALL_CODES).contains(
                "SALES_DISCOUNT", "SALES_EXPORT", "SALES_AFIP_RETRY", "CASH_HISTORY_VIEW",
                "STOCK_VIEW", "STOCK_ADJUST", "CLIENTS_ACCOUNT_MANAGE", "SUPPLIERS_ACCOUNT_MANAGE",
                "PURCHASES_VIEW", "PURCHASES_EDIT", "PURCHASES_CONFIRM", "USERS_MANAGE", "ROLES_MANAGE");
        assertThat(PermissionService.ALL_CODES).doesNotHaveDuplicates();
        assertThat(PermissionService.CATALOG.stream()
                .filter(e -> e[0].equals("SALES_VIEW"))
                .findFirst().orElseThrow()[1]).isEqualTo("Ver ventas");
    }

    @Test
    void requireExistingCodesReturnsEntitiesWhenAllExist() {
        when(repository.findAllById(any())).thenReturn(List.of(permission("SALES_VIEW"), permission("SALES_CREATE")));

        Set<PermissionEntity> result = service.requireExistingCodes(List.of("SALES_VIEW", "SALES_CREATE"));

        assertThat(result).extracting(PermissionEntity::getCode).containsExactlyInAnyOrder("SALES_VIEW", "SALES_CREATE");
    }

    @Test
    void requireExistingCodesRejectsUnknownCodes() {
        when(repository.findAllById(any())).thenReturn(List.of(permission("SALES_VIEW")));

        assertThatThrownBy(() -> service.requireExistingCodes(List.of("SALES_VIEW", "NOPE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void catalogContainsPlatformAdmin() {
        assertThat(PermissionService.ALL_CODES).contains("PLATFORM_ADMIN");
        assertThat(PermissionService.isPlatformCode("PLATFORM_ADMIN")).isTrue();
        assertThat(PermissionService.isPlatformCode("USERS_MANAGE")).isFalse();
    }

    @Test
    void catalogHidesPlatformCodesFromNonPlatformAdmin() {
        when(repository.findAll()).thenReturn(List.of(permission("SALES_VIEW"), permission("PLATFORM_ADMIN")));
        when(permissionGuard.isPlatformAdmin()).thenReturn(false);

        assertThat(service.getAllPermissions()).extracting(PermissionEntity::getCode).containsExactly("SALES_VIEW");
    }

    @Test
    void catalogShowsPlatformCodesToPlatformAdmin() {
        when(repository.findAll()).thenReturn(List.of(permission("SALES_VIEW"), permission("PLATFORM_ADMIN")));
        when(permissionGuard.isPlatformAdmin()).thenReturn(true);

        assertThat(service.getAllPermissions()).extracting(PermissionEntity::getCode)
                .containsExactlyInAnyOrder("SALES_VIEW", "PLATFORM_ADMIN");
    }

    @Test
    void requireExistingCodesRejectsNullOrBlank() {
        assertThatThrownBy(() -> service.requireExistingCodes(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.requireExistingCodes(Arrays.asList("SALES_VIEW", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
