package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.exception.ResourceAlreadyExistsException;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;




    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }


    @Transactional
    public UserResponseDto createUser(UserEntity userEntity) {
        userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword()));
        userEntity.setEnabled(true);
        return userMapper.userToUserDto(userRepository.save(userEntity));
    }

    // Buscar usuario por nombre de usuario
    public Optional<UserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // Verificar si existe el usuario
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    // Obtener todos los usuarios
    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }

    // Actualizar usuario
    @Transactional
    public UserEntity updateUser(Long id, String newPassword, RoleEntity newRole) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (newRole != null) {
            user.setRole(newRole);
        }

        return userRepository.save(user);
    }

    // Eliminar usuario
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void userValidation(String user){
        if (userRepository.existsByUsername(user))
            throw new ResourceAlreadyExistsException("El usuario ya existe : " + user);

    }


}
