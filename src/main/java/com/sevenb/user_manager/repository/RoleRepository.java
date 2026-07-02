package com.sevenb.user_manager.repository;

import com.sevenb.user_manager.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    List<RoleEntity> findByTenantId(String tenantId);
    Optional<RoleEntity> findByNameAndTenantId(String name, String tenantId);
    boolean existsByNameAndTenantId(String name, String tenantId);
    boolean existsByTenantId(String tenantId);
    Optional<RoleEntity> findByIdAndTenantId(Long id, String tenantId);
}
