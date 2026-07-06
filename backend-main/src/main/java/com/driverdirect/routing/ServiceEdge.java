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
     *  Always {@code after} itself for a virtual road edge (no schedule);
     *  {@code null} if no departure exists in the planning horizon. */
    Instant nextDeparture(Instant after);

    Instant arrivalTime(Instant departure);

    double cost(CargoDetails cargo);

    double co2(CargoDetails cargo);
}
