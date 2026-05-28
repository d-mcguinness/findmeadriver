package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, Long> {

    List<DriverAvailability> findByDriverAndDateBetween(Driver driver, LocalDate start, LocalDate end);

    List<DriverAvailability> findByDriverAndDateIn(Driver driver, Collection<LocalDate> dates);

    Optional<DriverAvailability> findByDriverAndDate(Driver driver, LocalDate date);

    void deleteByDriverAndDate(Driver driver, LocalDate date);
}
