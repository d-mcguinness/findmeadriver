package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.service.PricingPolicy;

/**
 * A rate card compiled into search-friendly primitives: carrier cost =
 * {@code max(minimumCharge, baseFee + ratePerUnit × quantity)} — the same
 * formula {@code PricingService} applies at the Shipment boundary. Doubles,
 * not BigDecimal: this is the objective function evaluated per edge
 * relaxation (see {@link Label}); a winning option's legs are re-priced
 * through PricingService before anything is persisted, so the authoritative
 * money never comes from here.
 *
 * <p>All mapping from the authoritative {@link PricingPolicy.RateCard} goes
 * through {@link #from} — the same single-seam rule as
 * {@link CargoDetails#from}.
 */
public record Tariff(ChargeUnit unit, double baseFee, double ratePerUnit, double minimumCharge) {

    public static Tariff from(PricingPolicy.RateCard card) {
        return new Tariff(card.unit(), card.baseFee().doubleValue(),
                card.ratePerUnit().doubleValue(), card.minimumCharge().doubleValue());
    }

    /**
     * Estimated carrier cost for {@code cargo} over {@code distanceKm}. When
     * the cargo doesn't carry the quantity this tariff meters (e.g. a
     * PER_CONTAINER edge and {@code containerCount} is null), the minimum
     * charge stands in as the best-known floor — the planner needs an
     * estimate, not a refusal.
     *
     * <p>PER_KM caveat: {@code distanceKm} here is a coordinate model —
     * haversine, times {@code RoutePlanner.ROAD_CIRCUITY} for a virtual road
     * edge — not measured driving distance. It does <em>not</em> make the
     * estimate a lower bound on the reprice, because accepting a route
     * persists this very number onto the {@code Shipment} (tagged
     * {@code DistanceSource.GREAT_CIRCLE_ESTIMATE}) and PricingService then
     * meters PER_KM off it: estimate and reprice agree by construction. What
     * the caveat costs is absolute accuracy against the real world — a road
     * leg still reads short of true driving distance, and a sea leg ignores
     * canals and straits entirely. Compare against a hand-posted load, whose
     * distance is CLIENT_SUPPLIED (the form measures it via the Routes API),
     * only with that difference in mind.
     */
    public double cost(CargoDetails cargo, double distanceKm) {
        Double quantity = quantityFor(cargo, distanceKm);
        if (quantity == null || quantity <= 0) return Math.max(minimumCharge, baseFee);
        return Math.max(minimumCharge, baseFee + ratePerUnit * quantity);
    }

    /** Mirrors PricingService.chargeableQuantity; null when the cargo lacks
     *  the metric this unit needs. */
    private Double quantityFor(CargoDetails cargo, double distanceKm) {
        switch (unit) {
            case PER_KM:
                return distanceKm > 0 ? distanceKm : null;
            case PER_CONTAINER:
                return cargo.containerCount() != null ? cargo.containerCount().doubleValue() : null;
            case PER_PIECE:
                return cargo.pieceCount() != null ? cargo.pieceCount().doubleValue() : null;
            case PER_CHARGEABLE_KG:
                return chargeableKg(cargo);
            case FLAT:
                return 1.0;
            case PER_HOUR:
            default:
                return null; // no hourly basis on a routing edge — floor applies
        }
    }

    /** Air chargeable weight = max(actual kg, volumetric kg); volumetric =
     *  m³ × 1,000,000 / 6000 (IATA) — same rule as PricingService.chargeableKg. */
    private Double chargeableKg(CargoDetails cargo) {
        Double actual = cargo.weightKg() != null ? cargo.weightKg().doubleValue() : null;
        Double volumetric = cargo.volumeM3() != null
                ? cargo.volumeM3().doubleValue() * 1_000_000
                        / PricingPolicy.AIR_VOLUMETRIC_DIVISOR.doubleValue()
                : null;
        if (actual == null) return volumetric;
        if (volumetric == null) return actual;
        return Math.max(actual, volumetric);
    }
}
