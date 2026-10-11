package com.sevenb.user_manager.security;

public final class TenantContext {

    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<Long> ownerId = new ThreadLocal<>();
    private static final ThreadLocal<String> username = new ThreadLocal<>();
    private static final ThreadLocal<String> role = new ThreadLocal<>();
    private static final ThreadLocal<String> taxCondition = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenant, Long user, Long owner, String userName, String userRole,
                           String userTaxCondition) {
        tenantId.set(tenant);
        userId.set(user);
        ownerId.set(owner);
        username.set(userName);
        role.set(userRole);
        taxCondition.set(userTaxCondition);
    }

    public static String getTenantId() {
        return tenantId.get();
    }

    public static Long getUserId() {
        return userId.get();
    }

    public static Long getOwnerId() {
        return ownerId.get();
    }

    public static String getUsername() {
        return username.get();
    }

    public static String getRole() {
        return role.get();
    }

    public static String getTaxCondition() {
        return taxCondition.get();
    }

    public static String requireTenantId() {
        String tenant = tenantId.get();
        if (tenant == null || tenant.isBlank()) {
            throw new UnauthorizedException("tenantId no disponible en el contexto");
        }
        return tenant;
    }

    public static Long requireOwnerId() {
        Long owner = ownerId.get();
        if (owner == null) {
            throw new UnauthorizedException("ownerId no disponible en el contexto");
        }
        return owner;
    }

    public static void clear() {
        tenantId.remove();
        userId.remove();
        ownerId.remove();
        username.remove();
        role.remove();
        taxCondition.remove();
    }
}
