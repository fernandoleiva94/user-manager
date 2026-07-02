package com.sevenb.user_manager.dto;

import java.util.List;

public class UserPermissionOverrideRequest {

    private List<OverrideEntry> overrides;

    public List<OverrideEntry> getOverrides() { return overrides; }
    public void setOverrides(List<OverrideEntry> overrides) { this.overrides = overrides; }

    public static class OverrideEntry {

        private String permissionCode;
        private boolean granted;

        public String getPermissionCode() { return permissionCode; }
        public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

        public boolean isGranted() { return granted; }
        public void setGranted(boolean granted) { this.granted = granted; }
    }
}
