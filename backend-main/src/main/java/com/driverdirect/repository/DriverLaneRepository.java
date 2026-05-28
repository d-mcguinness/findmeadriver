package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverLane;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverLaneRepository extends JpaRepository<DriverLane, Long> {

    List<DriverLane> findByDriverOrderByOriginCountryAscDestinationCountryAsc(Driver driver);

    Optional<DriverLane> findByDriverAndOriginCountryAndDestinationCountry(
            Driver driver, String originCountry, String destinationCountry);

    long countByDriver(Driver driver);
}
