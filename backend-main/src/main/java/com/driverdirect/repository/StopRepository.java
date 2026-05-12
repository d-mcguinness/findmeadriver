package com.driverdirect.repository;

import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StopRepository extends JpaRepository<Stop, Long> {
    List<Stop> findByShipmentOrderBySequenceAsc(Shipment shipment);
}