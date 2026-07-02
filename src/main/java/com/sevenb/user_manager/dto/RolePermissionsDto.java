package com.sevenb.user_manager.dto;

import java.util.Set;

public class RolePermissionsDto {

    private Set<String> permissionCodes;

    public Set<String> getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(Set<String> permissionCodes) { this.permissionCodes = permissionCodes; }
}
