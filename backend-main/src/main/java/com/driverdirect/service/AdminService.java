package com.driverdirect.service;

import com.driverdirect.model.Role;
import com.driverdirect.model.User;

import java.util.List;
import java.util.Optional;

public interface AdminService {
    // Delete methods
    void deleteAllUsers();
    void deleteAllRoles();
    void deleteAll();
    void deleteUserById(Long id);
    void deleteRoleById(Long id);
    void deleteUserByEmail(String email);

    // Create methods
    User createUser(User user);
    Role createRole(Role role);

    // Read methods
    List<User> getAllUsers();
    User getUserById(Long id);
    User getUserByEmail(String email);
    List<Role> getAllRoles();
    Role getRoleById(Long id);
    Optional<Role> getRoleByName(Role.RoleType roleType);

    // Update methods
    User updateUser(User user);
    Role updateRole(Role role);
}
