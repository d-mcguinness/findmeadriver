package com.driverdirect.routing;

import com.driverdirect.service.PricingPolicy;

/**
 * The cost + emission rates of one leg, compiled into search-friendly
 * primitives: a {@link Tariff} (from {@link PricingPolicy}), a carbon
 * intensity (kg CO2e per tonne-km, from {@code EmissionPolicy}), and the
 * mode's platform commission percentage. Bundled so an edge carries both
 * objective functions the Pareto search optimises — cost and CO2 — from one
 * immutable, JPA-free value, and so the mapping seams stay together
 * ({@link #from}).
 *
 * <p>Commission belongs here, not applied to a route total, because it is
 * <strong>per mode</strong> (ROAD 10% … AIR 20%): a route pays each leg's own
 * rate, exactly as {@code PricingService} charges each leg's Shipment. One
 * blended rate over a whole route would be wrong for every route spanning
 * modes, and would misrank them — a cheaper-to-haul mode can cost the shipper
 * more once its higher commission lands.
 *
 * <p>Doubles, not BigDecimal: these feed {@link Label} in the hot search
 * loop. A winning option's legs are re-priced through PricingService (and,
 * later, an emission snapshot) at the Shipment boundary, so authoritative
 * figures never come from here.
 */
public record LegRates(Tariff tariff, double co2PerTonneKm, double commissionPercent) {

    public static LegRates from(PricingPolicy.RateCard card, double co2PerTonneKm,
                                double commissionPercent) {
        return new LegRates(Tariff.from(card), co2PerTonneKm, commissionPercent);
    }

    /** What the carrier earns for this leg — the rate card alone. */
    public double carrierCost(CargoDetails cargo, double distanceKm) {
        return tariff.cost(cargo, distanceKm);
    }

    /** The platform's fee on this leg: this mode's percentage of carrier cost.
     *  Mirrors {@code PricingService}'s commissionAmount. */
    public double commission(CargoDetails cargo, double distanceKm) {
        return carrierCost(cargo, distanceKm) * commissionPercent / 100.0;
    }

    /** What the shipper pays for this leg — carrier cost plus this mode's
     *  commission. Mirrors {@code PricingService}'s shipperTotal, and is what
     *  the search optimises: a shipper comparing routes is comparing these,
     *  not carrier costs. */
    public double shipperCost(CargoDetails cargo, double distanceKm) {
        return carrierCost(cargo, distanceKm) + commission(cargo, distanceKm);
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
