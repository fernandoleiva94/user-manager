package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.management.relation.Role;
import java.util.List;
import java.util.Optional;

@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    public RoleEntity createRole(RoleEntity role) {
        return roleRepository.save(role);
    }

    public List<RoleEntity> getAllRoles() {
        return roleRepository.findAll();
    }

    public Optional<RoleEntity> getRoleById(Long id){
        return  roleRepository.findById(id);
    }

    public void deleteRole(Long id){
        roleRepository.deleteById(id);
    }
}