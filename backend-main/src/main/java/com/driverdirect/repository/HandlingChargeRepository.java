package com.driverdirect.repository;

import com.driverdirect.model.HandlingCharge;
import com.driverdirect.model.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandlingChargeRepository extends JpaRepository<HandlingCharge, Long> {

    List<HandlingCharge> findByItineraryOrderByAfterLegSequenceAsc(Itinerary itinerary);
}
