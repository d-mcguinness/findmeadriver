package com.driverdirect.repository;

import com.driverdirect.model.Shipper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipperRepository extends JpaRepository<Shipper, Long> {
    
    Optional<Shipper> findByEmail(String email);
    
    Optional<Shipper> findByCompanyName(String companyName);
    
    Boolean existsByCompanyName(String companyName);
}
