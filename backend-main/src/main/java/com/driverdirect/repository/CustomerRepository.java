package com.driverdirect.repository;

import com.driverdirect.model.Customer;
import com.driverdirect.model.Shipper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByShipper(Shipper shipper);
    Optional<Customer> findFirstByShipperOrderByIdAsc(Shipper shipper);
}
