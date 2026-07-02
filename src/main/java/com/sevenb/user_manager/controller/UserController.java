package com.sevenb.user_manager.controller;

import com.sevenb.user_manager.dto.ResolvedPermissionsDto;
import com.sevenb.user_manager.dto.UserCreateRequest;
import com.sevenb.user_manager.dto.UserPermissionOverrideDto;
import com.sevenb.user_manager.dto.UserPermissionOverrideRequest;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.dto.UserRoleDto;
import com.sevenb.user_manager.dto.UserUpdateRequest;
import com.sevenb.user_manager.service.PermissionResolutionService;
import com.sevenb.user_manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PermissionResolutionService permissionResolutionService;

    @Autowired
    public UserController(UserService userService, PermissionResolutionService permissionResolutionService) {
        this.userService = userService;
        this.permissionResolutionService = permissionResolutionService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserResponseDto> getUserByUsername(@PathVariable String username) {
        Optional<UserResponseDto> user = userService.findByUsername(username);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<UserResponseDto> getAllUsers() {
        return userService.getAllUsersForCurrentTenant();
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@RequestBody UserCreateRequest request) {
        UserResponseDto createdUser = userService.createUser(request);
        return ResponseEntity.ok(createdUser);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateRequest request) {
        UserResponseDto updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/role")
    public ResponseEntity<UserRoleDto> getUserRole(@PathVariable Long userId) {
        UserRoleDto dto = userService.getUserRole(userId);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<UserResponseDto> assignRole(
            @PathVariable Long userId,
            @RequestBody UserRoleDto dto) {
        UserResponseDto updated = userService.assignRole(userId, dto.getRoleId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{userId}/permissions")
    public ResponseEntity<List<UserPermissionOverrideDto>> getUserOverrides(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getOverrides(userId));
    }

    @PutMapping("/{userId}/permissions")
    public ResponseEntity<Void> setUserOverrides(
            @PathVariable Long userId,
            @RequestBody UserPermissionOverrideRequest request) {
        userService.setOverrides(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/resolved-permissions")
    public ResponseEntity<ResolvedPermissionsDto> getResolvedPermissions(@PathVariable Long userId) {
        ResolvedPermissionsDto dto = new ResolvedPermissionsDto();
        dto.setPerms(permissionResolutionService.resolvePermissions(userId));
        return ResponseEntity.ok(dto);
    }
}
