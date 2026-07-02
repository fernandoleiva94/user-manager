package com.sevenb.user_manager.dto;

public class UserPermissionOverrideDto {

    private Long id;
    private String permissionCode;
    private boolean granted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
