package com.sevenb.user_manager.dto;

import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.UserEntity;

public class RegisterDTO {

    private Person person;
    private UserEntity user;

    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
}
