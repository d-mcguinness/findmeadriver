package com.driverdirect.service;

import com.driverdirect.model.Role;
import com.driverdirect.model.User;
import com.driverdirect.repository.RoleRepository;
import com.driverdirect.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void deleteAllUsers() {
        userRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteAllRoles() {
        roleRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteAll() {
        deleteAllUsers();
        deleteAllRoles();
    }

    @Override
    @Transactional
    public void deleteUserById(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteRoleById(Long id) {
        roleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteUserByEmail(String email) {
        userRepository.deleteByEmail(email);
    }

    @Override
    @Transactional
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public Role createRole(Role role) {
        return roleRepository.save(role);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
    }

    @Override
    public Optional<Role> getRoleByName(Role.RoleType roleType) {
        return roleRepository.findByName(roleType);
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public Role updateRole(Role role) {
        return roleRepository.save(role);
    }
}
