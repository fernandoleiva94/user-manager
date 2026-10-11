package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.repository.RoleRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import com.sevenb.user_manager.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class RoleService {

    public static final String ADMIN_ROLE = "ADMIN";
    private static final List<String> ADMIN_REQUIRED_CODES = List.of("USERS_MANAGE", "ROLES_MANAGE");
    private static final String PLATFORM_CODES_MESSAGE = "Los permisos de plataforma no se asignan por rol";

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final CurrentUserService currentUserService;
    private final PermissionGuard permissionGuard;

    public RoleService(RoleRepository roleRepository, PermissionService permissionService,
                       CurrentUserService currentUserService, PermissionGuard permissionGuard) {
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
        this.currentUserService = currentUserService;
        this.permissionGuard = permissionGuard;
    }

    public RoleEntity createRole(RoleEntity role) {
        String tenantId = TenantContext.requireTenantId();
        if (isAdminRole(role)) {
            throw new IllegalArgumentException("El rol ADMIN es del sistema y no se puede crear ni redefinir");
        }
        // Sin id: este endpoint solo crea; no debe servir para sobrescribir (renombrar) un rol existente.
        role.setId(null);
        role.setTenantId(tenantId);
        if (role.getPermissions() != null) {
            Set<String> codes = role.getPermissions().stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
            PermissionService.rejectPlatformCodes(codes, PLATFORM_CODES_MESSAGE);
            role.setPermissions(permissionService.requireExistingCodes(codes));
        }
        return roleRepository.save(role);
    }

    public List<RoleEntity> getRolesForCurrentTenant() {
        return roleRepository.findByTenantId(TenantContext.requireTenantId());
    }

    public Optional<RoleEntity> getRoleById(Long id) {
        return roleRepository.findByIdAndTenantId(id, TenantContext.requireTenantId());
    }

    public void deleteRole(Long id) {
        RoleEntity role = roleRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + id));
        if (isAdminRole(role)) {
            throw new IllegalArgumentException("El rol ADMIN no se puede eliminar");
        }
        roleRepository.delete(role);
    }

    public Set<PermissionEntity> getPermissions(Long roleId) {
        RoleEntity role = roleRepository.findByIdAndTenantId(roleId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
        if (permissionGuard.isPlatformAdmin()) {
            return role.getPermissions();
        }
        return role.getPermissions().stream()
                .filter(p -> !PermissionService.isPlatformCode(p.getCode()))
                .collect(Collectors.toSet());
    }

    public RoleEntity setPermissions(Long roleId, Set<String> permissionCodes) {
        RoleEntity role = roleRepository.findByIdAndTenantId(roleId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
        PermissionService.rejectPlatformCodes(permissionCodes, PLATFORM_CODES_MESSAGE);
        Set<PermissionEntity> permissions = permissionService.requireExistingCodes(permissionCodes);
        currentUserService.currentUser()
                .filter(current -> current.getRole() != null && role.getId().equals(current.getRole().getId()))
                .filter(current -> !isAdminRole(current.getRole()))
                .ifPresent(current -> {
                    throw new IllegalArgumentException("No podés modificar los permisos de tu propio rol");
                });
        if (isAdminRole(role)) {
            assertAdminKeepsPermissions(role, permissionCodes);
        }
        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    public static boolean isAdminRole(RoleEntity role) {
        return role != null && ADMIN_ROLE.equalsIgnoreCase(role.getName());
    }

    private void assertAdminKeepsPermissions(RoleEntity adminRole, Set<String> newCodes) {
        Set<String> removed = adminRole.getPermissions().stream()
                .map(PermissionEntity::getCode)
                .filter(code -> !PermissionService.isPlatformCode(code))
                .filter(code -> !newCodes.contains(code))
                .collect(Collectors.toCollection(TreeSet::new));
        for (String required : ADMIN_REQUIRED_CODES) {
            if (!newCodes.contains(required)) {
                removed.add(required);
            }
        }
        if (!removed.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se pueden quitar permisos al rol ADMIN: " + String.join(", ", removed));
        }
    }

    public RoleEntity getRoleForTenant(Long roleId, String tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
    }

    public RoleEntity getRoleByNameForTenant(String name, String tenantId) {
        return roleRepository.findByNameAndTenantId(name, tenantId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + name));
    }

    public void seedDefaultRolesForTenant(String tenantId) {
        if (roleRepository.existsByTenantId(tenantId)) return;

        Set<String> allCodes = new HashSet<>(PermissionService.ALL_CODES);
        allCodes.removeIf(PermissionService::isPlatformCode);

        Set<String> supervisorCodes = new HashSet<>(allCodes);
        supervisorCodes.removeAll(Set.of("SETTINGS_EDIT", "TERMINALS_MANAGE", "BRANCHES_MANAGE",
                "USERS_MANAGE", "ROLES_MANAGE"));

        Set<String> cajeroCodes = Set.of(
                "SALES_CREATE", "SALES_VIEW", "SALES_AFIP_RETRY", "PRODUCTS_VIEW",
                "CLIENTS_VIEW", "CASH_SESSIONS_MANAGE"
        );

        createSeededRole(tenantId, ADMIN_ROLE,   "Administrador con acceso total",     allCodes);
        createSeededRole(tenantId, "SUPERVISOR", "Supervisor con acceso ampliado",      supervisorCodes);
        createSeededRole(tenantId, "CAJERO",     "Cajero con acceso limitado",          cajeroCodes);
    }

    private void createSeededRole(String tenantId, String name, String description, Set<String> codes) {
        RoleEntity role = new RoleEntity();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissionService.findByCodes(codes));
        roleRepository.save(role);
    }
}
