package com.driverdirect.config;

import com.driverdirect.model.Role;
import com.driverdirect.model.User;
import com.driverdirect.repository.RoleRepository;
import com.driverdirect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Initialize roles if they don't exist
        initRoles();

        // Create test users if they don't exist
        createTestUsers();
    }

    private void initRoles() {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(null, Role.RoleType.ROLE_ADMIN));
            roleRepository.save(new Role(null, Role.RoleType.ROLE_EMPLOYER));
            roleRepository.save(new Role(null, Role.RoleType.ROLE_DRIVER));
        }
    }

    private void createTestUsers() {
        if (userRepository.count() == 0) {
            // Admin user
            createUser(
                "admin@driverdirect.com",
                "admin123",
                "Admin",
                "User",
                Set.of(Role.RoleType.ROLE_ADMIN)
            );

            // Employer user
            createUser(
                "employer@company.com",
                "employer123",
                "Employer",
                "User",
                Set.of(Role.RoleType.ROLE_EMPLOYER)
            );

            // Driver user
            createUser(
                "driver@example.com",
                "driver123",
                "Driver",
                "User",
                Set.of(Role.RoleType.ROLE_DRIVER)
            );
        }
    }

    private void createUser(String email, String password,
                           String firstName, String lastName, Set<Role.RoleType> roleTypes) {
        User user = new User(email, passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);

        Set<Role> roles = new HashSet<>();
        roleTypes.forEach(roleType -> {
            Role role = roleRepository.findByName(roleType)
                .orElseThrow(() -> new RuntimeException("Role not found"));
            roles.add(role);
        });

        user.setRoles(roles);
        userRepository.save(user);
    }
}
