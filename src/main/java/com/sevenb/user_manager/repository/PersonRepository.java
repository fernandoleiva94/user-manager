package com.sevenb.user_manager.repository;

import com.sevenb.user_manager.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
    boolean existsByEmail(String email);
    boolean existsByDocumentNumber(String email);
}
