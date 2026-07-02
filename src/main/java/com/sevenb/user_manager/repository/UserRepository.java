package com.sevenb.user_manager.repository;

import com.sevenb.user_manager.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByUsernameAndPerson_TenantId(String username, String tenantId);
    List<UserEntity> findByPerson_TenantId(String tenantId);
    Optional<UserEntity> findByIdAndPerson_TenantId(Long id, String tenantId);
    Optional<UserEntity> findByUsernameAndPerson_TenantId(String username, String tenantId);
}
