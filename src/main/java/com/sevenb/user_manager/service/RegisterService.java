package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final PersonService personService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final RoleService roleService;

    public RegisterService(PersonService personService, UserService userService,
                           SubscriptionService subscriptionService, RoleService roleService) {
        this.personService = personService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.roleService = roleService;
    }

    @Transactional
    public UserResponseDto register(RegisterDTO register) {
        personService.mailValiation(register.getPerson().getEmail());
        personService.documentNumberValidate(register.getPerson().getDocumentNumber());
        userService.userValidation(register.getUser().getUsername());
        Person person = personService.createPerson(register.getPerson());
        roleService.seedDefaultRolesForTenant(person.getTenantId());
        register.getUser().setPerson(person);
        UserResponseDto userResponseDto = userService.createUserForRegister(register.getUser());
        subscriptionService.subscribeFreePlan(person.getId());
        return userResponseDto;
    }
}
