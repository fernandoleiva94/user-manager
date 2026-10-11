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

    public boolean hasAny(String... codes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.JwtClaims claims)) {
            return false;
        }

        if (ADMIN_ROLE.equalsIgnoreCase(claims.role())) {
            return true;
        }

        List<String> required = Arrays.asList(codes);
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
}
