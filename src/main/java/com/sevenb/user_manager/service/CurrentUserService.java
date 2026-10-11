package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Usuario autenticado resuelto contra la base (username del token + tenant), para que las reglas
 * de escalada de privilegios no dependan del rol embebido en un token que puede estar desactualizado.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserEntity> currentUser() {
        String username = TenantContext.getUsername();
        String tenantId = TenantContext.getTenantId();
        if (username == null || tenantId == null) {
            return Optional.empty();
        }
        return userRepository.findByUsernameAndPerson_TenantId(username, tenantId);
    }

    public boolean isCurrentUserAdmin() {
        return currentUser().map(user -> RoleService.isAdminRole(user.getRole())).orElse(false);
    }

    public boolean isCurrentUser(UserEntity user) {
        String username = TenantContext.getUsername();
        return username != null && user != null && username.equals(user.getUsername());
    }
}
