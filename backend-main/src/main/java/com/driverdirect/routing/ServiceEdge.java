package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.time.Instant;

/**
 * One traversable edge in the routing graph: a scheduled carrier service
 * (rail/sea/air) or a synthetic road leg generated on demand. Deliberately
 * mode-specific rather than transfer-aware — a mode change at a shared
 * location is costed by the search via {@link TransferProfile}, not by a
 * separate edge type (see README.md, "Proposed: multimodal routing engine").
 */
public interface ServiceEdge {

    Long originLocationId();

    Long destinationLocationId();

    Shipment.Mode mode();

    /** Earliest instant this edge can be boarded at or after {@code after}.
     *  Always {@code after} itself for a virtual road edge (no schedule).
     *  {@code null} means this service has no departures at all (empty
     *  recurring pattern — the graph build never emits such edges); there is
     *  no planning horizon: a non-empty pattern always yields a departure
     *  within 8 days of {@code after}, however far in the future. */
    Instant nextDeparture(Instant after);

    Instant arrivalTime(Instant departure);

    double cost(CargoDetails cargo);

    double co2(CargoDetails cargo);
}
