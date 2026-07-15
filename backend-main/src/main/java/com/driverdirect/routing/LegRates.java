package com.driverdirect.routing;

import com.driverdirect.service.PricingPolicy;

/**
 * The cost + emission rates of one leg, compiled into search-friendly
 * primitives: a {@link Tariff} (from {@link PricingPolicy}) plus a carbon
 * intensity (kg CO2e per tonne-km, from {@code EmissionPolicy}). Bundled so
 * an edge carries both objective functions the Pareto search optimises —
 * cost and CO2 — from one immutable, JPA-free value, and so the two
 * mapping seams stay together ({@link #from}).
 *
 * <p>Doubles, not BigDecimal: these feed {@link Label} in the hot search
 * loop. A winning option's legs are re-priced through PricingService (and,
 * later, an emission snapshot) at the Shipment boundary, so authoritative
 * figures never come from here.
 */
public record LegRates(Tariff tariff, double co2PerTonneKm) {

    public static LegRates from(PricingPolicy.RateCard card, double co2PerTonneKm) {
        return new LegRates(Tariff.from(card), co2PerTonneKm);
    }

    public double cost(CargoDetails cargo, double distanceKm) {
        return tariff.cost(cargo, distanceKm);
    }

    /** Emissions for {@code cargo} over {@code distanceKm}:
     *  {@code distance × tonnes × factor}. 0 when the distance is unknown or
     *  the cargo carries no weight (the search then simply doesn't
     *  differentiate that leg on CO2 — it doesn't mislead). Uses actual mass,
     *  not air chargeable weight: emissions track physical tonnage. */
    public double co2(CargoDetails cargo, double distanceKm) {
        if (distanceKm <= 0) return 0;
        double tonnes = cargo != null && cargo.weightKg() != null
                ? cargo.weightKg().doubleValue() / 1000.0
                : 0;
        return distanceKm * tonnes * co2PerTonneKm;
    }
}
