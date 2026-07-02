package com.sevenb.user_manager.repository;

import com.sevenb.user_manager.entity.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {
    List<UserPermissionOverride> findByUserId(Long userId);
    Optional<UserPermissionOverride> findByUserIdAndPermissionCode(Long userId, String permissionCode);
    void deleteByUserIdAndPermissionCode(Long userId, String permissionCode);
}
