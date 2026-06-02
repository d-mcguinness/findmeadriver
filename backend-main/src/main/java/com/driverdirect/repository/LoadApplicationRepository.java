package com.driverdirect.repository;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoadApplicationRepository extends JpaRepository<LoadApplication, Long> {

    List<LoadApplication> findByCarrierOrderByAppliedAtDesc(Carrier carrier);

    List<LoadApplication> findByLoad(Load load);

    /** Application counts for a set of loads in one query: rows of [loadId, count]. */
    @Query("select ja.load.id, count(ja) from LoadApplication ja where ja.load in :loads group by ja.load.id")
    List<Object[]> countByLoadIn(@Param("loads") List<Load> loads);

    List<LoadApplication> findByLoadAndStatus(Load load, ApplicationStatus status);

    Optional<LoadApplication> findByLoadAndCarrier(Load load, Carrier carrier);

    boolean existsByLoadAndCarrier(Load load, Carrier carrier);
}
