package com.sevenb.user_manager.controller;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<List<PermissionEntity>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }
}
