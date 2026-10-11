package com.sevenb.user_manager.controller;

import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.JwtService;
import com.sevenb.user_manager.security.PermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Evalúa las expresiones de {@code @PreAuthorize} de PersonController contra un PermissionGuard real. */
class PersonControllerSecurityTest {

    private final PermissionGuard enforcing = new PermissionGuard(true, mock(UserRepository.class));
    private final PermissionGuard shadow = new PermissionGuard(false, mock(UserRepository.class));

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

    private static String expression(String method, Class<?>... params) throws NoSuchMethodException {
        return PersonController.class.getMethod(method, params).getAnnotation(PreAuthorize.class).value();
    }

    private static boolean allows(PermissionGuard guard, String expression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver((ctx, name) -> guard);
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(expression).getValue(context, Boolean.class));
    }

    private static String update() throws NoSuchMethodException {
        return expression("updatePerson", Long.class, Person.class);
    }

    private static String delete() throws NoSuchMethodException {
        return expression("deletePerson", Long.class);
    }

    @Test
    void updateAllowedForPlatformAdmin() throws Exception {
        authenticate("CAJERO", List.of("PLATFORM_ADMIN"));
        assertThat(allows(enforcing, update())).isTrue();
    }

    @Test
    void updateAllowedWithSettingsEdit() throws Exception {
        authenticate("SUPERVISOR", List.of("SETTINGS_EDIT"));
        assertThat(allows(enforcing, update())).isTrue();
    }

    @Test
    void updateAllowedForAdminRoleBypass() throws Exception {
        authenticate("ADMIN", List.of());
        assertThat(allows(enforcing, update())).isTrue();
    }

    @Test
    void updateDeniedWithoutSettingsEditWhenEnforcing() throws Exception {
        authenticate("CAJERO", List.of("SALES_CREATE"));
        assertThat(allows(enforcing, update())).isFalse();
    }

    @Test
    void updateAllowedWithoutSettingsEditInShadowMode() throws Exception {
        authenticate("CAJERO", List.of("SALES_CREATE"));
        assertThat(allows(shadow, update())).isTrue();
    }

    @Test
    void deleteAllowedOnlyForPlatformAdmin() throws Exception {
        authenticate("CAJERO", List.of("PLATFORM_ADMIN"));
        assertThat(allows(enforcing, delete())).isTrue();
    }

    @Test
    void deleteDeniedForTenantAdminEvenInShadowMode() throws Exception {
        authenticate("ADMIN", List.of("SETTINGS_EDIT", "USERS_MANAGE"));
        assertThat(allows(enforcing, delete())).isFalse();
        assertThat(allows(shadow, delete())).isFalse();
    }

    @Test
    void deleteDeniedForLegacyToken() throws Exception {
        authenticate("ADMIN", null);
        assertThat(allows(shadow, delete())).isFalse();
    }
}
