package com.driverdirect.dto;

import com.driverdirect.model.Shipment;
import com.driverdirect.service.PricingPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Public, marketing-facing description of how we price each transport mode.
 * The commission percentage is read live from {@link PricingPolicy} (the single
 * source of truth used by the pricing engine); the label / basis / tagline are
 * presentation copy. Surfaced by the unauthenticated {@code /api/pricing/modes}
 * endpoint and rendered on the landing + pricing pages.
 */
@Data
@AllArgsConstructor
public class ModePricingResponse {
    private String mode;
    private String label;
    private BigDecimal commissionPercent;
    private String basis;
    private String tagline;

    private record Copy(String label, String basis, String tagline) {}

    private static final Map<Shipment.Mode, Copy> COPY = new EnumMap<>(Shipment.Mode.class);
    static {
        COPY.put(Shipment.Mode.ROAD, new Copy("Road",
                "Per kilometre + minimum charge",
                "Spare tachograph hours and full road haulage, matched in minutes."));
        COPY.put(Shipment.Mode.RAIL, new Copy("Rail",
                "Per container / wagon, terminal-to-terminal",
                "Low-carbon line-haul along the rail corridors."));
        COPY.put(Shipment.Mode.OCEAN, new Copy("Sea",
                "Per container (FCL) or per W/M revenue-ton (LCL)",
                "Port-to-port containers and groupage."));
        COPY.put(Shipment.Mode.AIR, new Copy("Air",
                "Per chargeable-kg (IATA volumetric weight)",
                "Time-critical freight, delivered fast."));
    }

    /** Modes we advertise on the marketing site, in display order. */
    private static final List<Shipment.Mode> MARKETED = List.of(
            Shipment.Mode.ROAD, Shipment.Mode.RAIL, Shipment.Mode.OCEAN, Shipment.Mode.AIR);

    public static List<ModePricingResponse> marketed(PricingPolicy policy) {
        List<ModePricingResponse> out = new ArrayList<>();
        for (Shipment.Mode m : MARKETED) {
            Copy c = COPY.get(m);
            out.add(new ModePricingResponse(
                    m.name(), c.label(), policy.commissionPercentFor(m), c.basis(), c.tagline()));
        }
        return out;
    }
}
