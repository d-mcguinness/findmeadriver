package com.driverdirect.service;

import com.driverdirect.dto.DriverRegistrationRequest;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Role;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.RoleRepository;
import com.driverdirect.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;

@AllArgsConstructor
@Service
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Driver createDriver(DriverRegistrationRequest registrationRequest) {
        // Check if email is already in use
        if (userRepository.existsByEmail(registrationRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Check if license number is already in use
        if (driverRepository.existsByLicenseNumber(registrationRequest.getLicenseNumber())) {
            throw new IllegalArgumentException("License number is already registered");
        }

        // Create new driver
        Driver driver = new Driver(
            registrationRequest.getEmail(),
            passwordEncoder.encode(registrationRequest.getPassword()),
            registrationRequest.getLicenseNumber(),
            registrationRequest.getLicenseExpiration()
        );

        // Set driver properties
        driver.setFirstName(registrationRequest.getFirstName());
        driver.setLastName(registrationRequest.getLastName());
        driver.setCdlType(registrationRequest.getCdlType());
        driver.setYearsExperience(registrationRequest.getYearsExperience());

        // Set driver role
        Role driverRole = roleRepository.findByName(Role.RoleType.ROLE_DRIVER)
            .orElseThrow(() -> new RuntimeException("Driver role not found"));
        driver.setRoles(new HashSet<>(Collections.singletonList(driverRole)));

        // Save driver to database
        return driverRepository.save(driver);
    }
}
