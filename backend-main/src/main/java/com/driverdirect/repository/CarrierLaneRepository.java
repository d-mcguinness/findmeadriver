package com.driverdirect.repository;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CarrierLaneRepository extends JpaRepository<CarrierLane, Long> {

    List<CarrierLane> findByCarrierOrderByOriginCountryAscDestinationCountryAsc(Carrier carrier);

    /**
     * Every lane that can back a routing-graph edge: a full timetable plus
     * both terminal anchors (the inner join fetch drops anchor-less lanes and
     * loads the terminals in the same query — the graph build stays at a
     * constant query count). Blank-but-non-null departureDays still needs the
     * Java-side {@code isTimetabled()} guard.
     */
    @Query("select l from CarrierLane l "
            + "join fetch l.originLocation "
            + "join fetch l.destinationLocation "
            + "where l.departureTime is not null "
            + "and l.transitDurationHours is not null "
            + "and l.departureDays is not null")
    List<CarrierLane> findTimetabledWithTerminals();

    Optional<CarrierLane> findByCarrierAndOriginCountryAndDestinationCountry(
            Carrier carrier, String originCountry, String destinationCountry);

    long countByCarrier(Carrier carrier);
}
