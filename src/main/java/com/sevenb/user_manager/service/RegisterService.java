package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RegisterService {

    private final PersonService personService;
    private final UserService userService;
    private final RoleService roleService;

    public RegisterService(PersonService personService, UserService userService, RoleService roleService) {
        this.personService = personService;
        this.userService = userService;
        this.roleService = roleService;
    }

    @Transactional
    public UserResponseDto register(RegisterDTO register) {
        personService.mailValiation(register.getPerson().getEmail());
        personService.documentNumberValidate(register.getPerson().getDocumentNumber());
        userService.userValidation(register.getUser().getUsername());
        register.getPerson().setId(null);
        register.getPerson().setTenantId(UUID.randomUUID().toString());
        register.getPerson().setCreatedAt(LocalDateTime.now());
        Person person = personService.createPerson(register.getPerson());
        roleService.seedDefaultRolesForTenant(person.getTenantId());
        register.getUser().setId(null);
        register.getUser().setPerson(person);
        register.getUser().setRole(roleService.getRoleByNameForTenant("ADMIN", person.getTenantId()));
        return userService.createUserForRegister(register.getUser());
    }
}
