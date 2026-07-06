package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.util.List;
import java.util.Map;

/**
 * The in-memory routing graph. Source of truth stays in the relational
 * tables (Location, CarrierLane, …) — this is a disposable, rebuildable
 * snapshot produced by {@link RoutingGraphBuilder}, not persisted itself.
 */
public record RoutingGraph(
        Map<Long, List<ServiceEdge>> edgesByOriginLocation,
        Map<Long, List<TransferProfile>> transferProfilesByLocation) {

    public List<ServiceEdge> edgesFrom(Long locationId) {
        return edgesByOriginLocation.getOrDefault(locationId, List.of());
    }

    /** {@code null} if no transfer profile is configured for that mode pair
     *  at that location — the search should treat that as "no transfer
     *  possible here", not as a free/zero-cost transfer. */
    public TransferProfile transferProfile(Long locationId, Shipment.Mode from, Shipment.Mode to) {
        return transferProfilesByLocation.getOrDefault(locationId, List.of()).stream()
                .filter(p -> p.fromMode() == from && p.toMode() == to)
                .findFirst()
                .orElse(null);
    }
}
