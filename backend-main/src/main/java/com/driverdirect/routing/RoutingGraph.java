package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The in-memory routing graph. Source of truth stays in the relational
 * tables (Location, CarrierLane, …) — this is a disposable, rebuildable
 * snapshot produced by {@link RoutingGraphBuilder}, not persisted itself.
 *
 * <p>The snapshot deliberately closes over <em>everything</em> a search
 * touches — scheduled edges, the location index (coordinates + zones, for
 * virtual road-edge generation), and the road tariff — so a query holding
 * one graph never reads live repositories or the live pricing bean
 * mid-search. All ~30 searches of a flexible-window query share one
 * consistent world; a concurrent lane or location edit only affects
 * subsequent queries, which build a fresh graph
 * (see {@code RoutePlannerService}).
 */
public record RoutingGraph(
        Map<Long, List<ServiceEdge>> edgesByOriginLocation,
        Map<Long, List<TransferProfile>> transferProfilesByLocation,
        Map<Long, LocationNode> locations,
        Tariff roadTariff) {

    public RoutingGraph {
        // Deep-freeze (Map.copyOf alone would leave the value lists mutable):
        // the immutability guarantee must hold however the graph was built,
        // not just via RoutingGraphBuilder.
        edgesByOriginLocation = deepFreeze(edgesByOriginLocation);
        transferProfilesByLocation = deepFreeze(transferProfilesByLocation);
        locations = Map.copyOf(locations);
    }

    private static <T> Map<Long, List<T>> deepFreeze(Map<Long, List<T>> byLocation) {
        return byLocation.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    public List<ServiceEdge> edgesFrom(Long locationId) {
        return edgesByOriginLocation.getOrDefault(locationId, List.of());
    }

    public LocationNode location(Long locationId) {
        return locations.get(locationId);
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
