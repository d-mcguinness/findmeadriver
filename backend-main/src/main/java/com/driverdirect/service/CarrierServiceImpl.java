package com.driverdirect.service;

import com.driverdirect.dto.CarrierRegistrationRequest;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Role;
import com.driverdirect.repository.CarrierRepository;
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
public class CarrierServiceImpl implements CarrierService {

    private final CarrierRepository carrierRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Carrier createCarrier(CarrierRegistrationRequest registrationRequest) {
        // Check if email is already in use
        if (userRepository.existsByEmail(registrationRequest.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Check if license number is already in use
        if (carrierRepository.existsByLicenseNumber(registrationRequest.getLicenseNumber())) {
            throw new IllegalArgumentException("License number is already registered");
        }

        // Create new carrier
        Carrier carrier = new Carrier(
            registrationRequest.getEmail(),
            passwordEncoder.encode(registrationRequest.getPassword()),
            registrationRequest.getLicenseNumber(),
            registrationRequest.getLicenseExpiration()
        );

        // Set carrier properties
        carrier.setFirstName(registrationRequest.getFirstName());
        carrier.setLastName(registrationRequest.getLastName());
        carrier.setLicenceCategory(registrationRequest.getLicenceCategory());
        carrier.setYearsExperience(registrationRequest.getYearsExperience());

        // Set carrier role
        Role carrierRole = roleRepository.findByName(Role.RoleType.ROLE_CARRIER)
            .orElseThrow(() -> new RuntimeException("Carrier role not found"));
        carrier.setRoles(new HashSet<>(Collections.singletonList(carrierRole)));

        // Save carrier to database
        return carrierRepository.save(carrier);
    }
}
