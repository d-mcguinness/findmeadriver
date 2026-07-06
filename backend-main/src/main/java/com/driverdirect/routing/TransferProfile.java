package com.driverdirect.routing;

import com.driverdirect.model.Shipment;

/**
 * Cost + dwell time to move cargo from one mode to another at a single
 * location (e.g. vessel → rail at a port). Looked up by the search whenever
 * a label's arrival mode differs from the next edge's mode — never a graph
 * edge itself, since that would require the graph's nodes to be
 * (location, mode) pairs instead of plain locations.
 */
public record TransferProfile(
        Long locationId,
        Shipment.Mode fromMode,
        Shipment.Mode toMode,
        double cost,
        double dwellMinutes) {
}
