package com.sevenb.user_manager.controller;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.service.RegisterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/register")
public class RegisterController {

    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }


    @PostMapping
    public ResponseEntity<UserResponseDto> registerPerson(@RequestBody RegisterDTO person) {
        UserResponseDto userResponseDto = registerService.register(person);
        return ResponseEntity.ok(userResponseDto);
    }
}
