package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByEmail(String email);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    Boolean existsByLicenseNumber(String licenseNumber);

    /** (driverId, supportedMode) rows for the given drivers — one query, used by
     *  the batch eligibility preview to avoid an N+1 over each driver's modes.
     *  Drivers with no rows are road-only (empty set). */
    @Query("select d.id, m from Driver d join d.supportedModes m where d in :drivers")
    List<Object[]> findSupportedModesByDrivers(@Param("drivers") Collection<Driver> drivers);
}
