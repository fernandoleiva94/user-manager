package com.sevenb.user_manager.service;



import com.sevenb.user_manager.dto.UserCreateRequest;

import com.sevenb.user_manager.dto.UserPermissionOverrideDto;

import com.sevenb.user_manager.dto.UserPermissionOverrideRequest;

import com.sevenb.user_manager.dto.UserResponseDto;

import com.sevenb.user_manager.dto.UserRoleDto;

import com.sevenb.user_manager.dto.UserUpdateRequest;

import com.sevenb.user_manager.entity.Person;

import com.sevenb.user_manager.entity.RoleEntity;

import com.sevenb.user_manager.entity.UserEntity;

import com.sevenb.user_manager.entity.UserPermissionOverride;

import com.sevenb.user_manager.exception.ResourceAlreadyExistsException;

import com.sevenb.user_manager.mapper.UserMapper;

import com.sevenb.user_manager.repository.PersonRepository;

import com.sevenb.user_manager.repository.UserPermissionOverrideRepository;

import com.sevenb.user_manager.repository.UserRepository;

import com.sevenb.user_manager.security.TenantContext;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;



import java.util.List;

import java.util.Optional;



@Service

public class UserService {



    private final UserRepository userRepository;

    private final PersonRepository personRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final RoleService roleService;

    private final UserPermissionOverrideRepository overrideRepository;

    private final PermissionService permissionService;

    private final CurrentUserService currentUserService;



    @Autowired

    public UserService(UserRepository userRepository, PersonRepository personRepository,

                       PasswordEncoder passwordEncoder, UserMapper userMapper,

                       RoleService roleService, UserPermissionOverrideRepository overrideRepository,

                       PermissionService permissionService, CurrentUserService currentUserService) {

        this.userRepository = userRepository;

        this.personRepository = personRepository;

        this.passwordEncoder = passwordEncoder;

        this.userMapper = userMapper;

        this.roleService = roleService;

        this.overrideRepository = overrideRepository;

        this.permissionService = permissionService;

        this.currentUserService = currentUserService;

    }



    @Transactional

    public UserResponseDto createUser(UserCreateRequest request) {

        String tenantId = TenantContext.requireTenantId();



        if (userRepository.existsByUsernameAndPerson_TenantId(request.getUsername(), tenantId)) {

            throw new ResourceAlreadyExistsException("El usuario ya existe: " + request.getUsername());

        }



        Long ownerId = TenantContext.requireOwnerId();

        Person person = personRepository.findByIdAndTenantId(ownerId, tenantId)

                .orElseThrow(() -> new RuntimeException("Persona no encontrada: " + ownerId));



        UserEntity userEntity = new UserEntity();

        userEntity.setUsername(request.getUsername());

        userEntity.setPassword(passwordEncoder.encode(request.getPassword()));

        userEntity.setPerson(person);

        userEntity.setEnabled(true);



        if (request.getRoleId() != null) {

            RoleEntity role = roleService.getRoleForTenant(request.getRoleId(), tenantId);

            if (RoleService.isAdminRole(role)) {

                assertCurrentUserIsAdmin("Solo un usuario ADMIN puede asignar el rol ADMIN");

            }

            userEntity.setRole(role);

        }



        return userMapper.userToUserDto(userRepository.save(userEntity));

    }



    @Transactional

    public UserResponseDto createUserForRegister(UserEntity userEntity) {

        userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword()));

        userEntity.setEnabled(true);

        return userMapper.userToUserDto(userRepository.save(userEntity));

    }



    public Optional<UserResponseDto> findByUsername(String username) {

        return userRepository.findByUsernameAndPerson_TenantId(username, TenantContext.requireTenantId())

                .map(userMapper::userToUserDto);

    }



    public List<UserResponseDto> getAllUsersForCurrentTenant() {

        return userRepository.findByPerson_TenantId(TenantContext.requireTenantId()).stream()

                .map(userMapper::userToUserDto)

                .toList();

    }



    @Transactional

    public UserResponseDto updateUser(Long id, UserUpdateRequest request) {

        UserEntity user = getUserForCurrentTenant(id);

        String tenantId = TenantContext.requireTenantId();

        if (RoleService.isAdminRole(user.getRole())) {

            assertCurrentUserIsAdmin("Solo un usuario ADMIN puede modificar a otro usuario ADMIN");

        }



        if (request.getPassword() != null && !request.getPassword().isEmpty()) {

            user.setPassword(passwordEncoder.encode(request.getPassword()));

        }

        if (request.getRoleId() != null) {

            RoleEntity newRole = roleService.getRoleForTenant(request.getRoleId(), tenantId);

            changeRole(user, newRole);

        }



        return userMapper.userToUserDto(userRepository.save(user));

    }



    @Transactional

    public void deleteUser(Long id) {

        UserEntity user = getUserForCurrentTenant(id);

        if (currentUserService.isCurrentUser(user)) {

            throw new IllegalArgumentException("No podés eliminar tu propio usuario");

        }

        if (RoleService.isAdminRole(user.getRole())) {

            assertCurrentUserIsAdmin("Solo un usuario ADMIN puede eliminar a otro usuario ADMIN");

            assertNotLastAdmin("No se puede eliminar al último usuario ADMIN de la empresa");

        }

        userRepository.delete(user);

    }



    public void userValidation(String username) {

        if (userRepository.existsByUsername(username))

            throw new ResourceAlreadyExistsException("El usuario ya existe: " + username);

    }



    public UserRoleDto getUserRole(Long userId) {

        UserEntity user = getUserForCurrentTenant(userId);

        UserRoleDto dto = new UserRoleDto();

        if (user.getRole() != null) {

            dto.setRoleId(user.getRole().getId());

        }

        return dto;

    }



    @Transactional

    public UserResponseDto assignRole(Long userId, Long roleId) {

        UserEntity user = getUserForCurrentTenant(userId);

        if (RoleService.isAdminRole(user.getRole())) {

            assertCurrentUserIsAdmin("Solo un usuario ADMIN puede modificar a otro usuario ADMIN");

        }

        RoleEntity role = roleService.getRoleForTenant(roleId, TenantContext.requireTenantId());

        changeRole(user, role);

        return userMapper.userToUserDto(userRepository.save(user));

    }



    public List<UserPermissionOverrideDto> getOverrides(Long userId) {

        getUserForCurrentTenant(userId);

        return overrideRepository.findByUserId(userId).stream()

                .map(userMapper::toOverrideDto)

                .toList();

    }



    @Transactional

    public void setOverrides(Long userId, UserPermissionOverrideRequest request) {

        UserEntity user = getUserForCurrentTenant(userId);

        assertNotCurrentUser(user, "No podés modificar tus propios permisos");

        if (request.getOverrides() == null) {

            throw new IllegalArgumentException("La lista de overrides es obligatoria");

        }

        permissionService.requireExistingCodes(request.getOverrides().stream()

                .map(UserPermissionOverrideRequest.OverrideEntry::getPermissionCode)

                .toList());



        for (UserPermissionOverrideRequest.OverrideEntry entry : request.getOverrides()) {

            Optional<UserPermissionOverride> existing =

                    overrideRepository.findByUserIdAndPermissionCode(userId, entry.getPermissionCode());



            if (existing.isPresent()) {

                existing.get().setGranted(entry.isGranted());

                overrideRepository.save(existing.get());

            } else {

                UserPermissionOverride override = new UserPermissionOverride();

                override.setUser(user);

                override.setPermissionCode(entry.getPermissionCode());

                override.setGranted(entry.isGranted());

                overrideRepository.save(override);

            }

        }

    }



    private void changeRole(UserEntity user, RoleEntity newRole) {

        boolean changes = user.getRole() == null || !newRole.getId().equals(user.getRole().getId());

        if (!changes) {

            return;

        }

        assertNotCurrentUser(user, "No podés cambiar tu propio rol");

        if (RoleService.isAdminRole(newRole)) {

            assertCurrentUserIsAdmin("Solo un usuario ADMIN puede asignar el rol ADMIN");

        }

        if (RoleService.isAdminRole(user.getRole()) && !RoleService.isAdminRole(newRole)) {

            assertNotLastAdmin("No se puede quitar el rol ADMIN al último usuario ADMIN de la empresa");

        }

        user.setRole(newRole);

    }



    private void assertCurrentUserIsAdmin(String message) {

        if (!currentUserService.isCurrentUserAdmin()) {

            throw new IllegalArgumentException(message);

        }

    }



    private void assertNotLastAdmin(String message) {

        long admins = userRepository.countByPerson_TenantIdAndRole_NameIgnoreCase(

                TenantContext.requireTenantId(), RoleService.ADMIN_ROLE);

        if (admins <= 1) {

            throw new IllegalArgumentException(message);

        }

    }



    private void assertNotCurrentUser(UserEntity user, String message) {

        String currentUsername = TenantContext.getUsername();

        if (currentUsername != null && currentUsername.equals(user.getUsername())) {

            throw new IllegalArgumentException(message);

        }

    }



    private UserEntity getUserForCurrentTenant(Long userId) {

        return userRepository.findByIdAndPerson_TenantId(userId, TenantContext.requireTenantId())

                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

    }

}


