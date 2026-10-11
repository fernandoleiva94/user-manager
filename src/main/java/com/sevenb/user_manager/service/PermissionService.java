package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.repository.PermissionRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionService implements ApplicationRunner {

    public static final List<String[]> CATALOG = Arrays.asList(
            new String[]{"DASHBOARD_VIEW",      "Ver dashboard"},
            new String[]{"SALES_VIEW",           "Ver ventas"},
            new String[]{"SALES_VIEW_ALL",       "Ver todas las ventas"},
            new String[]{"SALES_CREATE",         "Crear ventas"},
            new String[]{"PRODUCTS_VIEW",        "Ver productos"},
            new String[]{"PRODUCTS_EDIT",        "Editar productos"},
            new String[]{"SUPPLIERS_VIEW",       "Ver proveedores"},
            new String[]{"SUPPLIERS_EDIT",       "Editar proveedores"},
            new String[]{"CLIENTS_VIEW",         "Ver clientes"},
            new String[]{"CLIENTS_EDIT",         "Editar clientes"},
            new String[]{"BRANCHES_VIEW",        "Ver sucursales"},
            new String[]{"BRANCHES_MANAGE",      "Gestionar sucursales"},
            new String[]{"TERMINALS_VIEW",       "Ver terminales"},
            new String[]{"TERMINALS_MANAGE",     "Gestionar terminales"},
            new String[]{"REPORTS_VIEW",         "Ver reportes"},
            new String[]{"CASH_SESSIONS_MANAGE", "Gestionar sesiones de caja"},
            new String[]{"SETTINGS_EDIT",        "Editar configuración"},
            new String[]{"SALES_DISCOUNT",           "Aplicar descuentos en ventas"},
            new String[]{"SALES_EXPORT",             "Exportar ventas"},
            new String[]{"SALES_AFIP_RETRY",         "Reintentar autorización ARCA"},
            new String[]{"CASH_HISTORY_VIEW",        "Ver historial de cajas"},
            new String[]{"STOCK_VIEW",               "Ver stock y remitos"},
            new String[]{"STOCK_ADJUST",             "Ajustar stock y gestionar remitos"},
            new String[]{"CLIENTS_ACCOUNT_MANAGE",   "Registrar cobros y ajustes en cuenta corriente de clientes"},
            new String[]{"SUPPLIERS_ACCOUNT_MANAGE", "Registrar pagos, cargos y ajustes en cuenta corriente de proveedores"},
            new String[]{"PURCHASES_VIEW",           "Ver compras"},
            new String[]{"PURCHASES_EDIT",           "Crear, editar y anular compras"},
            new String[]{"PURCHASES_CONFIRM",        "Confirmar compras y aplicar stock"},
            new String[]{"USERS_MANAGE",             "Gestionar usuarios"},
            new String[]{"ROLES_MANAGE",             "Gestionar roles y permisos"},
            new String[]{"PLATFORM_ADMIN",           "Administrador de la plataforma Centauro"}
    );

    public static final List<String> ALL_CODES = CATALOG.stream()
            .map(p -> p[0])
            .collect(Collectors.toList());

    /** Los códigos de plataforma nunca se asignan por rol: solo por override concedido. */
    public static final String PLATFORM_PREFIX = "PLATFORM_";

    private final PermissionRepository permissionRepository;
    private final PermissionGuard permissionGuard;

    public PermissionService(PermissionRepository permissionRepository, PermissionGuard permissionGuard) {
        this.permissionRepository = permissionRepository;
        this.permissionGuard = permissionGuard;
    }

    public static boolean isPlatformCode(String code) {
        return code != null && code.startsWith(PLATFORM_PREFIX);
    }

    /** Lanza IllegalArgumentException (400) si la lista incluye algún código de plataforma. */
    public static void rejectPlatformCodes(Collection<String> codes, String message) {
        if (codes == null) {
            return;
        }
        List<String> platform = codes.stream()
                .filter(PermissionService::isPlatformCode)
                .distinct()
                .sorted()
                .toList();
        if (!platform.isEmpty()) {
            throw new IllegalArgumentException(message + ": " + String.join(", ", platform));
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String[] entry : CATALOG) {
            if (!permissionRepository.existsById(entry[0])) {
                PermissionEntity p = new PermissionEntity();
                p.setCode(entry[0]);
                p.setDescription(entry[1]);
                permissionRepository.save(p);
            }
        }
    }

    public List<PermissionEntity> getAllPermissions() {
        List<PermissionEntity> all = permissionRepository.findAll();
        if (permissionGuard.isPlatformAdmin()) {
            return all;
        }
        return all.stream()
                .filter(p -> !isPlatformCode(p.getCode()))
                .toList();
    }

    public Set<PermissionEntity> findByCodes(Set<String> codes) {
        return new HashSet<>(permissionRepository.findAllById(codes));
    }

    /**
     * Devuelve las entidades de los códigos pedidos o lanza IllegalArgumentException (400)
     * si alguno no existe en la tabla permissions.
     */
    public Set<PermissionEntity> requireExistingCodes(Collection<String> codes) {
        if (codes == null) {
            throw new IllegalArgumentException("La lista de permisos es obligatoria");
        }
        if (codes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Código de permiso vacío");
        }
        Set<String> requested = new HashSet<>(codes);
        Set<PermissionEntity> found = new HashSet<>(permissionRepository.findAllById(requested));
        Set<String> foundCodes = found.stream().map(PermissionEntity::getCode).collect(Collectors.toSet());
        List<String> missing = requested.stream()
                .filter(code -> !foundCodes.contains(code))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Códigos de permiso inexistentes: " + String.join(", ", missing));
        }
        return found;
    }
}
