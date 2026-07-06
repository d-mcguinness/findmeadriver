package com.driverdirect.routing;

/**
 * Builds a {@link RoutingGraph} snapshot from the relational tables. Stub —
 * intentionally not wired to any repository yet, since CarrierLane has no
 * timetable columns and Location has no transfer-profile table (README.md
 * build order steps 1–2 land first). Not a Spring bean for the same reason:
 * nothing should construct a RoutingGraph until there's real data to build
 * one from.
 */
public class RoutingGraphBuilder {

    public RoutingGraph build() {
        throw new UnsupportedOperationException(
                "RoutingGraphBuilder needs CarrierLane timetables + Location transfer profiles first");
    }
}
