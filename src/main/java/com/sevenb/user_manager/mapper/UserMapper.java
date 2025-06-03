package com.sevenb.user_manager.mapper;

import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.UserEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.Mapping;

@Component
public class UserMapper {

    public UserResponseDto userToUserDto(UserEntity userEntity){
        UserResponseDto userResponseDto = new UserResponseDto();
        userResponseDto.setId(userEntity.getId());
        userResponseDto.setUsername(userEntity.getUsername());
        userResponseDto.setEnabled(userEntity.isEnabled());
        userResponseDto.setRoleId(userEntity.getRole().getId());
        userResponseDto.setPersonId(userEntity.getPerson().getId());
        return userResponseDto;

    }

}
