package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarrierRepository extends JpaRepository<Carrier, Long> {

    Optional<Carrier> findByEmail(String email);

    Optional<Carrier> findByLicenseNumber(String licenseNumber);

    Boolean existsByLicenseNumber(String licenseNumber);

    /** (carrierId, supportedMode) rows for the given carriers — one query, used by
     *  the batch eligibility preview to avoid an N+1 over each carrier's modes.
     *  Carriers with no rows are road-only (empty set). */
    @Query("select d.id, m from Carrier d join d.supportedModes m where d in :carriers")
    List<Object[]> findSupportedModesByCarriers(@Param("carriers") Collection<Carrier> carriers);

    /** (carrierId, credential) rows for the given carriers — one query, used by
     *  the batch eligibility preview to avoid an N+1 over each carrier's creds. */
    @Query("select c.id, cr from Carrier c join c.credentials cr where c in :carriers")
    List<Object[]> findCredentialsByCarriers(@Param("carriers") Collection<Carrier> carriers);
}
