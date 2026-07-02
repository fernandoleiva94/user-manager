package com.sevenb.user_manager.repository;

import com.sevenb.user_manager.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    boolean existsByEmail(String email);
    boolean existsByDocumentNumber(String email);
    Optional<Person> findByIdAndTenantId(Long id, String tenantId);
}
