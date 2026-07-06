package com.driverdirect.routing;

import java.time.LocalDate;

/**
 * A shipper's routing request — mirrors the product goal in README.md: a
 * hard arrival deadline (a filter, never an optimisation axis) and a
 * flexible handover window the search runs once per candidate day within.
 *
 * <p>{@code TransportOrder} now has matching nullable columns
 * (earliestReadyDate/latestHandoverDate/arrivalDeadline) alongside its
 * existing required dateNeeded — the date-model tension this record was
 * originally flagged against is resolved. Once an option is accepted,
 * dateNeeded gets set to whichever handover date the winning
 * {@link RouteOption} actually used; these three fields carry over as
 * reference context, not the authoritative date.
 */
public record RouteQuery(
        Long originLocationId,
        Long destinationLocationId,
        CargoDetails cargo,
        LocalDate earliestReady,
        LocalDate latestHandover,
        LocalDate arrivalDeadline) {
}
