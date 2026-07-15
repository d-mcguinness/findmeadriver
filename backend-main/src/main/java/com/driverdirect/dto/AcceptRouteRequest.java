package com.driverdirect.dto;

import com.driverdirect.routing.CargoDetails;
import com.driverdirect.routing.RouteQuery;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Accept a proposed route (the routing engine's "integration point"): the
 * same query that produced the options, plus {@code legs} identifying which
 * returned option to accept — the client echoes back the chosen
 * {@link RouteOptionResponse}'s legs verbatim (origin/destination Location
 * ids + mode). The server re-plans, matches that leg sequence to a current
 * option, and materialises it as a draft {@code Itinerary} through the
 * existing intermodal-create flow. Nothing but the query + selector is
 * trusted: distances and pricing come from the re-planned option, not the
 * client.
 */
@Data
public class AcceptRouteRequest {

    private Long originLocationId;
    private Long destinationLocationId;

    private BigDecimal weightKg;
    private BigDecimal volumeM3;
    private Integer containerCount;
    private Integer pieceCount;

    private LocalDate earliestReady;
    private LocalDate latestHandover;
    private LocalDate arrivalDeadline;

    private String title;
    private String description;
    private String currency;

    /** The chosen option's legs, copied from the RouteOptionResponse. */
    private List<AcceptedLeg> legs;

    public RouteQuery toQuery() {
        return new RouteQuery(
                originLocationId, destinationLocationId,
                new CargoDetails(weightKg, volumeM3, containerCount, pieceCount),
                earliestReady, latestHandover, arrivalDeadline);
    }

    @Data
    public static class AcceptedLeg {
        private Long originLocationId;
        private Long destinationLocationId;
        private String mode;
    }
}
