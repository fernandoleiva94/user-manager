package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.mapper.UserMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final PersonService personService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    public RegisterService(PersonService personService, UserService userService, SubscriptionService subscriptionService) {
        this.personService = personService;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public UserResponseDto register(RegisterDTO register){
        personService.mailValiation(register.getPerson().getEmail());
        personService.documentNumberValidate(register.getPerson().getDocumentNumber());
        userService.userValidation(register.getUser().getUsername());
        Person person = personService.createPerson(register.getPerson());
        register.getUser().setPerson(person);
        UserResponseDto userResponseDto = userService.createUser(register.getUser());
        subscriptionService.subscribeFreePlan(person.getId());
        return userResponseDto;

    }

}
