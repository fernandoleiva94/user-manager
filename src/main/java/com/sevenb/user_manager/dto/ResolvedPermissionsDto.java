package com.sevenb.user_manager.dto;

import java.util.List;

public class ResolvedPermissionsDto {

    private List<String> perms;

    public List<String> getPerms() { return perms; }
    public void setPerms(List<String> perms) { this.perms = perms; }
}
