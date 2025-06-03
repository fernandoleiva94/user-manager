package com.sevenb.user_manager.dto;

import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDto {
    private Long id;
    private String username;
    private Long personId;
    private Long roleId;
    private boolean enabled ;


}
