package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.repository.UserRepository;
import jakarta.transaction.Transactional;
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



    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public UserEntity createUser(String username, String password, Set<RoleEntity> roles) {
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("El nombre de usuario ya existe");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles(roles);
        user.setEnabled(true);

        return userRepository.save(user);
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
    public UserEntity updateUser(Long id, String newPassword, Set<RoleEntity> newRoles) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (newRoles != null) {
            user.setRoles(newRoles);
        }

        return userRepository.save(user);
    }

    // Eliminar usuario
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
