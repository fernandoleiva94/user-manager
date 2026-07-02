package com.sevenb.user_manager.dto;

public class UserUpdateRequest {

    private String password;
    private Long roleId;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
