package com.driverdirect.repository;

import com.driverdirect.model.CabotageOperation;
import com.driverdirect.model.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CabotageOperationRepository extends JpaRepository<CabotageOperation, Long> {

    List<CabotageOperation> findByCarrierAndCountryAndPerformedAtGreaterThanEqual(
            Carrier carrier, String country, LocalDate since);

    /** All ops for several carriers in one country since a date — for batched
     *  per-carrier counting (admin eligibility preview). */
    List<CabotageOperation> findByCarrierInAndCountryAndPerformedAtGreaterThanEqual(
            Collection<Carrier> carriers, String country, LocalDate since);

    List<CabotageOperation> findByCarrierAndPerformedAtGreaterThanEqualOrderByCountryAscPerformedAtDesc(
            Carrier carrier, LocalDate since);
}
