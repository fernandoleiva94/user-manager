package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.mapper.UserMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final PersonService personService;
    private final UserService userService;

    public RegisterService(PersonService personService, UserService userService) {
        this.personService = personService;

        this.userService = userService;
    }

    @Transactional
    public UserResponseDto register(RegisterDTO register){
        personService.mailValiation(register.getPerson().getEmail());
        personService.documentNumberValidate(register.getPerson().getDocumentNumber());
        userService.userValidation(register.getUser().getUsername());
        personService.createPerson(register.getPerson());

        return userService.createUser(register.getUser());

    }

}
