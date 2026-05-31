package com.driverdirect.repository;

import com.driverdirect.model.Shipper;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByShipperOrderByCreatedAtDesc(Shipper shipper);
    List<Shipment> findByStatusOrderByCreatedAtDesc(Shipment.ShipmentStatus status);
    List<Shipment> findByItineraryOrderByLegSequenceAsc(Itinerary itinerary);
}