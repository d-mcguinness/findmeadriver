package com.driverdirect.service;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Shipment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-mode platform commission rates (M1b). The platform's charge varies by
 * transport mode and is applied on top of the carrier cost — see
 * {@link PricingService}.
 *
 * <p>For now these are code-level defaults so the dev H2 (create-drop) database
 * prices correctly with no seeded rate-card data. Phases M2/M3 replace this
 * with a per-mode/per-lane/per-carrier {@code RateCard} lookup; this bean then
 * becomes the fallback when no card matches.
 */
@Component
public class PricingPolicy {

    /** Default commission, used when a mode has no explicit rate. */
    private static final BigDecimal DEFAULT_PERCENT = new BigDecimal("10");

    // Higher modes cost more to broker (air/sea forwarding, customs, handling).
    private static final Map<Shipment.Mode, BigDecimal> COMMISSION_PERCENT =
            new EnumMap<>(Shipment.Mode.class);
    static {
        COMMISSION_PERCENT.put(Shipment.Mode.ROAD, new BigDecimal("10"));
        COMMISSION_PERCENT.put(Shipment.Mode.RAIL, new BigDecimal("12"));
        COMMISSION_PERCENT.put(Shipment.Mode.OCEAN, new BigDecimal("15"));
        COMMISSION_PERCENT.put(Shipment.Mode.AIR, new BigDecimal("20"));
        COMMISSION_PERCENT.put(Shipment.Mode.INTERMODAL, new BigDecimal("12"));
        COMMISSION_PERCENT.put(Shipment.Mode.PARCEL, new BigDecimal("18"));
    }

    /** Commission percentage (e.g. 15 = 15%) for the given mode. */
    public BigDecimal commissionPercentFor(Shipment.Mode mode) {
        if (mode == null) return COMMISSION_PERCENT.get(Shipment.Mode.ROAD);
        return COMMISSION_PERCENT.getOrDefault(mode, DEFAULT_PERCENT);
    }

    // ---- Per-mode carrier-cost rate cards (M3b) ----

    /** A mode's tariff: carrier cost = max(minimumCharge, baseFee + ratePerUnit × quantity). */
    public record RateCard(ChargeUnit unit, BigDecimal baseFee, BigDecimal ratePerUnit,
                           BigDecimal minimumCharge) {}

    /** IATA dimensional-weight divisor: volumetric kg = volume_cm³ / 6000. */
    public static final BigDecimal AIR_VOLUMETRIC_DIVISOR = new BigDecimal("6000");

    private static final Map<Shipment.Mode, RateCard> RATE_CARDS = new EnumMap<>(Shipment.Mode.class);
    static {
        // ROAD: distance-metered with a minimum. RAIL/OCEAN: per loading unit
        // (container). AIR: per chargeable-kg. PARCEL: per piece.
        RATE_CARDS.put(Shipment.Mode.ROAD,
                new RateCard(ChargeUnit.PER_KM, bd("50"), bd("1.20"), bd("150")));
        RATE_CARDS.put(Shipment.Mode.RAIL,
                new RateCard(ChargeUnit.PER_CONTAINER, bd("0"), bd("600"), bd("600")));
        RATE_CARDS.put(Shipment.Mode.OCEAN,
                new RateCard(ChargeUnit.PER_CONTAINER, bd("350"), bd("1800"), bd("1800")));
        RATE_CARDS.put(Shipment.Mode.AIR,
                new RateCard(ChargeUnit.PER_CHARGEABLE_KG, bd("0"), bd("3.20"), bd("75")));
        RATE_CARDS.put(Shipment.Mode.PARCEL,
                new RateCard(ChargeUnit.PER_PIECE, bd("0"), bd("8.50"), bd("8.50")));
        // INTERMODAL is never a leg mode; legs resolve to a concrete mode above.
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    /** Tariff for a mode, or null when the mode has no rate card (price falls
     *  back to rate × hours). */
    public RateCard rateCardFor(Shipment.Mode mode) {
        return mode == null ? null : RATE_CARDS.get(mode);
    }
}
