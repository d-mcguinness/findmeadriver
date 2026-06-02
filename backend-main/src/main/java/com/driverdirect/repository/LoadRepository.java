package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LoadRepository extends JpaRepository<Load, Long> {

    List<Load> findByShipperOrderByCreatedAtDesc(Shipper shipper);

    // dateNeeded now lives on TransportOrder via Shipment → ShipmentLine → Order.
    // Spring Data can't derive an ORDER BY through a collection-typed join, so
    // these are explicit JPQL.
    @Query("select j from Load j " +
           "left join j.shipment s " +
           "left join s.shipmentLines sl " +
           "left join sl.order o " +
           "where j.status = :status " +
           "and j.requiredLicenceCategory = :licenceCategory " +
           "order by o.dateNeeded asc")
    List<Load> findByStatusAndRequiredLicenceCategoryOrderByDateNeededAsc(
            @Param("status") LoadStatus status,
            @Param("licenceCategory") String licenceCategory);

    @Query("select j from Load j " +
           "left join j.shipment s " +
           "left join s.shipmentLines sl " +
           "left join sl.order o " +
           "where j.status = :status " +
           "order by o.dateNeeded asc")
    List<Load> findByStatusOrderByDateNeededAsc(@Param("status") LoadStatus status);

    // ---- Per-mode duty-clock consumption: committed hours from a carrier's
    // assigned/in-progress/completed loads, bucketed by mode + date. The mode and
    // dateNeeded live on the Shipment → ShipmentLine → Order tree, so these are JPQL.

    /** Committed hours for one carrier on one mode + date (the AVAILABILITY gate). */
    @Query("select coalesce(sum(j.estimatedDurationHours), 0) from Load j " +
           "left join j.shipment s left join s.shipmentLines sl left join sl.order o " +
           "where j.assignedCarrier = :carrier and j.status in :statuses " +
           "and s.mode = :mode and o.dateNeeded = :date")
    double sumCommittedHours(@Param("carrier") Carrier carrier,
                             @Param("statuses") Collection<LoadStatus> statuses,
                             @Param("mode") Shipment.Mode mode,
                             @Param("date") LocalDate date);

    /** Committed hours per carrier on one mode + date, one query (batch preview, no N+1).
     *  Rows are [carrierId, sumHours]. */
    @Query("select j.assignedCarrier.id, coalesce(sum(j.estimatedDurationHours), 0) from Load j " +
           "left join j.shipment s left join s.shipmentLines sl left join sl.order o " +
           "where j.assignedCarrier in :carriers and j.status in :statuses " +
           "and s.mode = :mode and o.dateNeeded = :date " +
           "group by j.assignedCarrier.id")
    List<Object[]> sumCommittedHoursByCarrier(@Param("carriers") Collection<Carrier> carriers,
                                              @Param("statuses") Collection<LoadStatus> statuses,
                                              @Param("mode") Shipment.Mode mode,
                                              @Param("date") LocalDate date);

    /** Committed hours per mode for one carrier across a date range (duty-clock windows).
     *  Rows are [mode, sumHours]. */
    @Query("select s.mode, coalesce(sum(j.estimatedDurationHours), 0) from Load j " +
           "left join j.shipment s left join s.shipmentLines sl left join sl.order o " +
           "where j.assignedCarrier = :carrier and j.status in :statuses " +
           "and o.dateNeeded between :start and :end " +
           "group by s.mode")
    List<Object[]> sumCommittedHoursByMode(@Param("carrier") Carrier carrier,
                                           @Param("statuses") Collection<LoadStatus> statuses,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end);
}
