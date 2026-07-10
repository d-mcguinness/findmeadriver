package com.driverdirect.service;

import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-mode carbon intensity — a code-default config bean mirroring
 * {@link PricingPolicy}. Factors are well-to-wheel kg CO2e per tonne-km,
 * GLEC Framework / ISO 14083 order-of-magnitude midpoints (sea ~10–15 g,
 * rail ~20–30 g, road ~60–100 g, air ~600–1000 g per t·km). A leg's
 * emissions = {@code distanceKm × tonnes × factor}, computed in
 * {@link com.driverdirect.routing.LegRates}.
 *
 * <p>Like {@code PricingPolicy}, this is consulted only at graph-build time
 * (the factor is compiled onto each edge via {@code LegRates}), so the
 * search never reads this bean mid-query. A per-lane/per-carrier emission
 * table with validity periods would later override these defaults, exactly
 * as the pricing rate cards will.
 */
@Component
public class EmissionPolicy {

    /** Fallback for a mode without an explicit factor. */
    private static final double DEFAULT_KG_PER_TONNE_KM = 0.075; // ~ road

    private static final Map<Shipment.Mode, Double> KG_PER_TONNE_KM =
            new EnumMap<>(Shipment.Mode.class);
    static {
        KG_PER_TONNE_KM.put(Shipment.Mode.ROAD, 0.075);
        KG_PER_TONNE_KM.put(Shipment.Mode.RAIL, 0.025);
        KG_PER_TONNE_KM.put(Shipment.Mode.OCEAN, 0.012);
        KG_PER_TONNE_KM.put(Shipment.Mode.AIR, 0.800);
        KG_PER_TONNE_KM.put(Shipment.Mode.PARCEL, 0.100);
        // INTERMODAL is never a leg mode; legs resolve to a concrete mode.
    }

    /** kg CO2e per tonne-km for the given mode. */
    public double kgCo2ePerTonneKm(Shipment.Mode mode) {
        if (mode == null) return DEFAULT_KG_PER_TONNE_KM;
        return KG_PER_TONNE_KM.getOrDefault(mode, DEFAULT_KG_PER_TONNE_KM);
    }
}
