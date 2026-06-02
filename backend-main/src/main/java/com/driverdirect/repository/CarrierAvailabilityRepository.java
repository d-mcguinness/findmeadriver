package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierAvailabilityRepository extends JpaRepository<CarrierAvailability, Long> {

    // All modes (used for listing + coarse cross-mode totals).
    List<CarrierAvailability> findByCarrierAndDateBetween(Carrier carrier, LocalDate start, LocalDate end);

    List<CarrierAvailability> findByCarrierAndDateIn(Carrier carrier, Collection<LocalDate> dates);

    // Per-mode (the duty-clock dimension).
    Optional<CarrierAvailability> findByCarrierAndDateAndMode(Carrier carrier, LocalDate date, Shipment.Mode mode);

    List<CarrierAvailability> findByCarrierAndDateBetweenAndMode(
            Carrier carrier, LocalDate start, LocalDate end, Shipment.Mode mode);

    List<CarrierAvailability> findByDateAndModeAndCarrierIn(
            LocalDate date, Shipment.Mode mode, Collection<Carrier> carriers);
}
