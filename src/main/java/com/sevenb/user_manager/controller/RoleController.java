package com.sevenb.user_manager.controller;

import com.sevenb.user_manager.dto.RolePermissionsDto;
import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.service.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("@perm.has('ROLES_MANAGE')")
    public ResponseEntity<RoleEntity> createRole(@RequestBody RoleEntity role) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(role));
    }

    @GetMapping
    @PreAuthorize("@perm.hasAny('USERS_MANAGE','ROLES_MANAGE')")
    public ResponseEntity<List<RoleEntity>> getRoles() {
        List<RoleEntity> roles = roleService.getRolesForCurrentTenant();
        if (roles.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('USERS_MANAGE','ROLES_MANAGE')")
    public ResponseEntity<RoleEntity> getRoleById(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ROLES_MANAGE')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        if (roleService.getRoleById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@perm.hasAny('USERS_MANAGE','ROLES_MANAGE')")
    public ResponseEntity<Set<String>> getRolePermissions(@PathVariable Long id) {
        Set<String> codes = roleService.getPermissions(id).stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(codes);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('ROLES_MANAGE')")
    public ResponseEntity<RoleEntity> setRolePermissions(
            @PathVariable Long id,
            @RequestBody RolePermissionsDto dto) {
        return ResponseEntity.ok(roleService.setPermissions(id, dto.getPermissionCodes()));
    }
}
