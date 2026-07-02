package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.PermissionEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.entity.UserPermissionOverride;
import com.sevenb.user_manager.repository.UserPermissionOverrideRepository;
import com.sevenb.user_manager.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionResolutionService {

    private final UserRepository userRepository;
    private final UserPermissionOverrideRepository overrideRepository;

    public PermissionResolutionService(UserRepository userRepository,
                                       UserPermissionOverrideRepository overrideRepository) {
        this.userRepository = userRepository;
        this.overrideRepository = overrideRepository;
    }

    public List<String> resolvePermissions(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

        Set<String> perms = new HashSet<>();
        if (user.getRole() != null) {
            perms = user.getRole().getPermissions().stream()
                    .map(PermissionEntity::getCode)
                    .collect(Collectors.toSet());
        }

        List<UserPermissionOverride> overrides = overrideRepository.findByUserId(userId);
        for (UserPermissionOverride override : overrides) {
            if (override.isGranted()) {
                perms.add(override.getPermissionCode());
            } else {
                perms.remove(override.getPermissionCode());
            }
        }

        return new ArrayList<>(perms);
    }
}
