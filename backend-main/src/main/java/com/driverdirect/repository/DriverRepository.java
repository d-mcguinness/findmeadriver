package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    
    Optional<Driver> findByEmail(String email);
    
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    
    Boolean existsByLicenseNumber(String licenseNumber);
}
