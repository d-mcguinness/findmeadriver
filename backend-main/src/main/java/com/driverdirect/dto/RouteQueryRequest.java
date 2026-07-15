package com.driverdirect.dto;

import com.driverdirect.routing.CargoDetails;
import com.driverdirect.routing.RouteQuery;
import lombok.Data;

import java.time.LocalDate;

/**
 * A shipper's route-planning request — the API shape of {@link RouteQuery}.
 * Origin/destination are existing {@code Location} ids (graph nodes); the
 * cargo quantities mirror the per-mode metrics the pricing/emission bases
 * meter. {@code earliestReady} is the one required date (the handover day
 * the search plans from); {@code latestHandover} is accepted for the
 * flexible-window search (step 5) but not yet explored; {@code
 * arrivalDeadline} is the hard filter.
 */
@Data
public class RouteQueryRequest {

    private Long originLocationId;
    private Long destinationLocationId;

    private java.math.BigDecimal weightKg;
    private java.math.BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;

    private LocalDate earliestReady;
    private LocalDate latestHandover;
    private LocalDate arrivalDeadline;

    public RouteQuery toQuery() {
        return new RouteQuery(
                originLocationId, destinationLocationId,
                new CargoDetails(weightKg, volumeM3, containerCount, pieceCount),
                earliestReady, latestHandover, arrivalDeadline);
    }
}
