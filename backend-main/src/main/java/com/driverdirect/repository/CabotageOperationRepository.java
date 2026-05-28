package com.driverdirect.repository;

import com.driverdirect.model.CabotageOperation;
import com.driverdirect.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CabotageOperationRepository extends JpaRepository<CabotageOperation, Long> {

    List<CabotageOperation> findByDriverAndCountryAndPerformedAtGreaterThanEqual(
            Driver driver, String country, LocalDate since);

    /** All ops for several drivers in one country since a date — for batched
     *  per-driver counting (admin eligibility preview). */
    List<CabotageOperation> findByDriverInAndCountryAndPerformedAtGreaterThanEqual(
            Collection<Driver> drivers, String country, LocalDate since);

    List<CabotageOperation> findByDriverAndPerformedAtGreaterThanEqualOrderByCountryAscPerformedAtDesc(
            Driver driver, LocalDate since);
}
