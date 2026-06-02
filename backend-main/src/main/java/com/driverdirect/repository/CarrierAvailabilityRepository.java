package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierAvailabilityRepository extends JpaRepository<CarrierAvailability, Long> {

    List<CarrierAvailability> findByCarrierAndDateBetween(Carrier carrier, LocalDate start, LocalDate end);

    List<CarrierAvailability> findByCarrierAndDateIn(Carrier carrier, Collection<LocalDate> dates);

    List<CarrierAvailability> findByDateAndCarrierIn(LocalDate date, Collection<Carrier> carriers);

    Optional<CarrierAvailability> findByCarrierAndDate(Carrier carrier, LocalDate date);

    void deleteByCarrierAndDate(Carrier carrier, LocalDate date);
}
