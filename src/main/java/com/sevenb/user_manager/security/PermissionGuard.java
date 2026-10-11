package com.sevenb.user_manager.security;

import com.sevenb.user_manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Evaluador de permisos para {@code @PreAuthorize("@perm.has('CODE')")}.
 * <p>
 * Con {@code centauro.security.permissions.enforce=false} (modo sombra) un permiso faltante
 * solo se registra con WARN y la request se permite.
 */
@Component("perm")
public class PermissionGuard {

    private static final Logger log = LoggerFactory.getLogger(PermissionGuard.class);
    private static final String ADMIN_ROLE = "ADMIN";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String PLATFORM_PREFIX = "PLATFORM_";

    private final boolean enforce;
    private final UserRepository userRepository;

    public PermissionGuard(@Value("${centauro.security.permissions.enforce:false}") boolean enforce,
                           UserRepository userRepository) {
        this.enforce = enforce;
        this.userRepository = userRepository;
    }

    public boolean has(String code) {
        return hasAny(code);
    }

    /**
     * Permite si {@code userId} es el propio usuario autenticado (mismo username y tenant);
     * si no, aplica {@link #hasAny(String...)} con sus reglas de bypass, legacy y modo sombra.
     */
    public boolean isSelfOrHasAny(Long userId, String... codes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.JwtClaims claims
                && userId != null && claims.username() != null) {
            boolean self = userRepository.findByIdAndPerson_TenantId(userId, claims.tenantId())
                    .map(user -> claims.username().equals(user.getUsername()))
                    .orElse(false);
            if (self) {
                return true;
            }
        }
        return hasAny(codes);
    }

    /**
     * Administrador de la plataforma Centauro: el claim {@code permissions} contiene
     * {@code PLATFORM_ADMIN}. Chequeo estricto: sin bypass de ADMIN, sin pase para tokens legacy
     * y sin modo sombra.
     */
    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.JwtClaims claims)) {
            return false;
        }
        List<String> granted = claims.permissions();
        return granted != null && granted.contains(PLATFORM_ADMIN);
    }

    /**
     * Como {@link #has(String)} pero sin modo sombra ni pase para tokens legacy.
     * Mantiene el bypass del rol ADMIN del tenant (salvo para códigos {@code PLATFORM_*}).
     */
    public boolean hasStrict(String code) {
        if (isPlatformCode(code)) {
            return isPlatformAdmin();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.JwtClaims claims)) {
            return false;
        }
        if (ADMIN_ROLE.equalsIgnoreCase(claims.role())) {
            return true;
        }
        List<String> granted = claims.permissions();
        return granted != null && granted.contains(code);
    }

    /**
     * Los códigos {@code PLATFORM_*} nunca se conceden por bypass de ADMIN, token legacy ni modo
     * sombra: para ellos decide {@link #isPlatformAdmin()}.
     */
    public boolean hasAny(String... codes) {
        List<String> platformCodes = Arrays.stream(codes).filter(PermissionGuard::isPlatformCode).toList();
        if (!platformCodes.isEmpty() && isPlatformAdmin()) {
            return true;
        }
        List<String> required = Arrays.stream(codes).filter(code -> !isPlatformCode(code)).toList();
        if (required.isEmpty()) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.JwtClaims claims)) {
            return false;
        }

        if (ADMIN_ROLE.equalsIgnoreCase(claims.role())) {
            return true;
        }

        List<String> granted = claims.permissions();
        if (granted == null) {
            log.warn("Token sin claim 'permissions' (legacy): se permite. usuario={} tenant={} permiso requerido={}",
                    claims.username(), claims.tenantId(), required);
            return true;
        }

        if (required.stream().anyMatch(granted::contains)) {
            return true;
        }

        if (enforce) {
            return false;
        }
        log.warn("[MODO SOMBRA] Permiso faltante, se permite. usuario={} tenant={} permiso requerido={}",
                claims.username(), claims.tenantId(), required);
        return true;
    }

    private static boolean isPlatformCode(String code) {
        return code != null && code.startsWith(PLATFORM_PREFIX);
    }
}
