package com.driverdirect.routing;

import java.util.List;

/**
 * Entry point for the routing engine (README.md, "Proposed: multimodal
 * routing engine"). Stub — the time-dependent multi-criteria search
 * (label-setting, deadline pruning, per-day flexible-window loop) isn't
 * implemented yet; this exists so the public API's shape is settled before
 * the search algorithm lands.
 */
public class RoutePlanner {

    private final RoutingGraph graph;

    public RoutePlanner(RoutingGraph graph) {
        this.graph = graph;
    }

    /**
     * Returns 3–6 Pareto-best (cost, CO2) options satisfying the deadline,
     * merged across every candidate handover day in the query's window; or,
     * if none satisfy the deadline, a single fastest-possible option instead.
     */
    public List<RouteOption> findOptions(RouteQuery query) {
        throw new UnsupportedOperationException("search algorithm not implemented yet");
    }
}
