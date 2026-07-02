package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.repository.RoleRepository;
import com.sevenb.user_manager.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;

    public RoleService(RoleRepository roleRepository, PermissionService permissionService) {
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
    }

    public RoleEntity createRole(RoleEntity role) {
        String tenantId = TenantContext.requireTenantId();
        role.setTenantId(tenantId);
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
        roleRepository.delete(role);
    }

    public Set<PermissionEntity> getPermissions(Long roleId) {
        RoleEntity role = roleRepository.findByIdAndTenantId(roleId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
        return role.getPermissions();
    }

    public RoleEntity setPermissions(Long roleId, Set<String> permissionCodes) {
        RoleEntity role = roleRepository.findByIdAndTenantId(roleId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
        role.setPermissions(permissionService.findByCodes(permissionCodes));
        return roleRepository.save(role);
    }

    public RoleEntity getRoleForTenant(Long roleId, String tenantId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleId));
    }

    public void seedDefaultRolesForTenant(String tenantId) {
        if (roleRepository.existsByTenantId(tenantId)) return;

        Set<String> allCodes = new HashSet<>(PermissionService.ALL_CODES);

        Set<String> supervisorCodes = new HashSet<>(allCodes);
        supervisorCodes.removeAll(Set.of("SETTINGS_EDIT", "TERMINALS_MANAGE", "BRANCHES_MANAGE"));

        Set<String> cajeroCodes = Set.of(
                "SALES_VIEW", "SALES_CREATE", "PRODUCTS_VIEW",
                "CLIENTS_VIEW", "CASH_SESSIONS_MANAGE"
        );

        createSeededRole(tenantId, "ADMIN",      "Administrador con acceso total",     allCodes);
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
