package com.driverdirect.config;

import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Role;
import com.driverdirect.model.User;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.EmployerRepository;
import com.driverdirect.repository.RoleRepository;
import com.driverdirect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initRoles();
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
            // Admin user (plain User — no subclass needed)
            User admin = new User("admin@driverdirect.com", passwordEncoder.encode("admin123"));
            admin.setFirstName("Admin");
            admin.setLastName("User");
            admin.setRoles(rolesOf(Role.RoleType.ROLE_ADMIN));
            userRepository.save(admin);

            // Employer user — must be Employer entity so EmployerRepository can find it
            Employer employer = new Employer(
                    "employer@company.com",
                    passwordEncoder.encode("employer123"),
                    "Acme Logistics"
            );
            employer.setFirstName("Employer");
            employer.setLastName("User");
            employer.setPhone("01234567890");
            employer.setIndustry(Employer.Industry.LOGISTICS);
            employer.setCompanySize(50);
            employer.setHeadquartersLocation("Dublin");
            employer.setRoles(rolesOf(Role.RoleType.ROLE_EMPLOYER));
            employerRepository.save(employer);

            // Driver user — must be Driver entity so DriverRepository can find it
            Driver driver = new Driver(
                    "driver@example.com",
                    passwordEncoder.encode("driver123"),
                    "DL-12345-IE",
                    LocalDate.now().plusYears(2)
            );
            driver.setFirstName("Driver");
            driver.setLastName("User");
            driver.setPhone("09876543210");
            driver.setCdlType(Driver.CDLType.CLASS_A);
            driver.setYearsExperience(5);
            driver.setRoles(rolesOf(Role.RoleType.ROLE_DRIVER));
            driverRepository.save(driver);
        }
    }

    private Set<Role> rolesOf(Role.RoleType... roleTypes) {
        Set<Role> roles = new HashSet<>();
        for (Role.RoleType rt : roleTypes) {
            roles.add(roleRepository.findByName(rt)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + rt)));
        }
        return roles;
    }
}