package com.sevenb.user_manager.dto;


import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.UserEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {

    private Person person;
    private UserEntity user;



}
