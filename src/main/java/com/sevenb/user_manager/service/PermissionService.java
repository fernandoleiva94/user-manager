package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.repository.PermissionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionService implements ApplicationRunner {

    public static final List<String[]> CATALOG = Arrays.asList(
            new String[]{"DASHBOARD_VIEW",      "Ver dashboard"},
            new String[]{"SALES_VIEW",           "Ver ventas propias"},
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
            new String[]{"SETTINGS_EDIT",        "Editar configuración"}
    );

    public static final List<String> ALL_CODES = CATALOG.stream()
            .map(p -> p[0])
            .collect(Collectors.toList());

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
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
        return permissionRepository.findAll();
    }

    public Set<PermissionEntity> findByCodes(Set<String> codes) {
        return new HashSet<>(permissionRepository.findAllById(codes));
    }
}
