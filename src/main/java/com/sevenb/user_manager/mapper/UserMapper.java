package com.sevenb.user_manager.mapper;

import com.sevenb.user_manager.dto.UserPermissionOverrideDto;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.entity.UserPermissionOverride;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDto userToUserDto(UserEntity userEntity) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(userEntity.getId());
        dto.setUsername(userEntity.getUsername());
        dto.setEnabled(userEntity.isEnabled());

        Person person = userEntity.getPerson();
        if (person != null) {
            dto.setPersonId(person.getId());
            dto.setFirstName(person.getFirstName());
            dto.setLastName(person.getLastName());
            dto.setEmail(person.getEmail());
            dto.setTenantId(person.getTenantId());
        }

        RoleEntity role = userEntity.getRole();
        if (role != null) {
            dto.setRoleId(role.getId());
            dto.setRoleName(role.getName());
        }

        return dto;
    }

    public UserPermissionOverrideDto toOverrideDto(UserPermissionOverride override) {
        UserPermissionOverrideDto dto = new UserPermissionOverrideDto();
        dto.setId(override.getId());
        dto.setPermissionCode(override.getPermissionCode());
        dto.setGranted(override.isGranted());
        return dto;
    }
}
