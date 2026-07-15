package com.driverdirect.service;

import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-mode carbon factors (kg CO2e per tonne-km). Pins the ordering that
 * drives the routing engine's CO2 axis — sea greenest, air dirtiest — and
 * the null/unknown fallback.
 */
class EmissionPolicyTest {

    private final EmissionPolicy policy = new EmissionPolicy();

    @Test
    void factorsRankSeaUnderRailUnderRoadUnderAir() {
        double ocean = policy.kgCo2ePerTonneKm(Shipment.Mode.OCEAN);
        double rail = policy.kgCo2ePerTonneKm(Shipment.Mode.RAIL);
        double road = policy.kgCo2ePerTonneKm(Shipment.Mode.ROAD);
        double air = policy.kgCo2ePerTonneKm(Shipment.Mode.AIR);
        assertThat(ocean).isLessThan(rail);
        assertThat(rail).isLessThan(road);
        assertThat(road).isLessThan(air);
        assertThat(ocean).isEqualTo(0.012);
        assertThat(air).isEqualTo(0.800);
    }

    @Test
    void nullModeFallsBackToADefault() {
        assertThat(policy.kgCo2ePerTonneKm(null)).isEqualTo(0.075);
    }
}
