package com.driverdirect.service;

import com.driverdirect.dto.EmployerRegistrationRequest;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Role;
import com.driverdirect.repository.EmployerRepository;
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
public class EmployerServiceImpl implements EmployerService {

    private final EmployerRepository employerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Employer createEmployer(EmployerRegistrationRequest registrationRequest) {
        // Check if email is already in use
        if (userRepository.existsByEmail(registrationRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Check if company name is already registered
        if (employerRepository.existsByCompanyName(registrationRequest.getCompanyName())) {
            throw new IllegalArgumentException("Company name is already registered");
        }

        // Create new employer
        Employer employer = new Employer(
            registrationRequest.getEmail(),
            passwordEncoder.encode(registrationRequest.getPassword()),
            registrationRequest.getCompanyName()
        );

        // Set employer properties
        employer.setFirstName(registrationRequest.getFirstName());
        employer.setLastName(registrationRequest.getLastName());
        employer.setCompanyName(registrationRequest.getCompanyName());

        // Set employer role
        Role employerRole = roleRepository.findByName(Role.RoleType.ROLE_EMPLOYER)
            .orElseThrow(() -> new RuntimeException("Employer role not found"));
        employer.setRoles(new HashSet<>(Collections.singletonList(employerRole)));

        // Save employer to database
        return employerRepository.save(employer);
    }
}
