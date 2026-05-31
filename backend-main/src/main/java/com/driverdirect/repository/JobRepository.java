package com.driverdirect.repository;

import com.driverdirect.model.Shipper;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByShipperOrderByCreatedAtDesc(Shipper shipper);

    // dateNeeded now lives on TransportOrder via Shipment → ShipmentLine → Order.
    // Spring Data can't derive an ORDER BY through a collection-typed join, so
    // these are explicit JPQL.
    @Query("select j from Job j " +
           "left join j.shipment s " +
           "left join s.shipmentLines sl " +
           "left join sl.order o " +
           "where j.status = :status " +
           "and j.requiredLicenceCategory = :licenceCategory " +
           "order by o.dateNeeded asc")
    List<Job> findByStatusAndRequiredLicenceCategoryOrderByDateNeededAsc(
            @Param("status") JobStatus status,
            @Param("licenceCategory") String licenceCategory);

    @Query("select j from Job j " +
           "left join j.shipment s " +
           "left join s.shipmentLines sl " +
           "left join sl.order o " +
           "where j.status = :status " +
           "order by o.dateNeeded asc")
    List<Job> findByStatusOrderByDateNeededAsc(@Param("status") JobStatus status);
}
