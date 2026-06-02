package com.driverdirect.repository;

import com.driverdirect.model.Customer;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.TransportOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransportOrderRepository extends JpaRepository<TransportOrder, Long> {
    List<TransportOrder> findByShipperOrderByCreatedAtDesc(Shipper shipper);
    List<TransportOrder> findByCustomerOrderByCreatedAtDesc(Customer customer);
}
