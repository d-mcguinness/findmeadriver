package com.driverdirect.repository;

import com.driverdirect.model.Shipper;
import com.driverdirect.model.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByShipperOrderByCreatedAtDesc(Shipper shipper);
}
