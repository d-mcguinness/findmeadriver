package com.driverdirect.service;

import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Code-default transfer capabilities and handling costs per terminal type —
 * the same pattern as {@link PricingPolicy}: sensible defaults so the dev
 * H2 (create-drop) database routes correctly with no seeded transfer data.
 * The per-location transfer-profile table (README build-order step 2,
 * second half) will override these; this bean then becomes the fallback
 * when a location has no explicit profiles.
 *
 * <p>Consulted only at graph-build time — {@code RoutingGraphBuilder}
 * compiles these defaults into the {@code RoutingGraph} snapshot as
 * {@code TransferProfile}s, so the search never reads this bean mid-query
 * (the snapshot-closure rule).
 */
@Component
public class TransferPolicy {

    /** Per-mode handling effort at a terminal: cost to (un)load cargo from
     *  that mode, and dwell before it's ready to move on. Placeholder
     *  figures, deliberately conservative. */
    private record Handling(double cost, double dwellMinutes) {}

    private static final Map<Shipment.Mode, Handling> HANDLING = Map.of(
            Shipment.Mode.ROAD, new Handling(50, 60),
            Shipment.Mode.RAIL, new Handling(100, 240),
            Shipment.Mode.OCEAN, new Handling(150, 360),
            Shipment.Mode.AIR, new Handling(80, 180));

    /** Which modes a terminal type can move cargo between. Fewer than two
     *  modes means no transfers happen there (ADDRESS in particular: a plain
     *  street address can't swap cargo between modes). */
    public Set<Shipment.Mode> modesHandledAt(Location.LocationType type) {
        if (type == null) return EnumSet.noneOf(Shipment.Mode.class);
        switch (type) {
            case SEAPORT:
                return EnumSet.of(Shipment.Mode.ROAD, Shipment.Mode.RAIL, Shipment.Mode.OCEAN);
            case AIRPORT:
                return EnumSet.of(Shipment.Mode.ROAD, Shipment.Mode.AIR);
            case RAIL_TERMINAL:
            case INLAND_TERMINAL:
                return EnumSet.of(Shipment.Mode.ROAD, Shipment.Mode.RAIL);
            case ADDRESS:
            default:
                return EnumSet.noneOf(Shipment.Mode.class);
        }
    }

    /** A transfer costs what its harder side costs: max of the two modes'
     *  handling. Crude, but consistent and easy to override later. */
    public double transferCost(Shipment.Mode from, Shipment.Mode to) {
        return Math.max(handling(from).cost(), handling(to).cost());
    }

    public double transferDwellMinutes(Shipment.Mode from, Shipment.Mode to) {
        return Math.max(handling(from).dwellMinutes(), handling(to).dwellMinutes());
    }

    private Handling handling(Shipment.Mode mode) {
        Handling h = HANDLING.get(mode);
        return h != null ? h : new Handling(100, 240); // unknown mode — conservative
    }
}
