package com.driverdirect.service;

import com.driverdirect.dto.ShipperRegistrationRequest;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Role;
import com.driverdirect.repository.ShipperRepository;
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
public class ShipperServiceImpl implements ShipperService {

    private final ShipperRepository shipperRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Shipper createShipper(ShipperRegistrationRequest registrationRequest) {
        // Check if email is already in use
        if (userRepository.existsByEmail(registrationRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Check if company name is already registered
        if (shipperRepository.existsByCompanyName(registrationRequest.getCompanyName())) {
            throw new IllegalArgumentException("Company name is already registered");
        }

        // Create new shipper
        Shipper shipper = new Shipper(
            registrationRequest.getEmail(),
            passwordEncoder.encode(registrationRequest.getPassword()),
            registrationRequest.getCompanyName()
        );

        // Set shipper properties
        shipper.setFirstName(registrationRequest.getFirstName());
        shipper.setLastName(registrationRequest.getLastName());
        shipper.setCompanyName(registrationRequest.getCompanyName());

        // Set shipper role
        Role shipperRole = roleRepository.findByName(Role.RoleType.ROLE_SHIPPER)
            .orElseThrow(() -> new RuntimeException("Shipper role not found"));
        shipper.setRoles(new HashSet<>(Collections.singletonList(shipperRole)));

        // Save shipper to database
        return shipperRepository.save(shipper);
    }
}
