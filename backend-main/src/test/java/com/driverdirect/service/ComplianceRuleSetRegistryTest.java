package com.driverdirect.service;

import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-mode duty/rest ceilings + the carrier→rule-set resolution. */
class ComplianceRuleSetRegistryTest {

    private final ComplianceRuleSetRegistry registry = new ComplianceRuleSetRegistry();

    @Test
    void ceilingsDifferByMode() {
        assertThat(registry.forMode(Shipment.Mode.ROAD).maxDailyHours()).isEqualTo(10.0);
        assertThat(registry.forMode(Shipment.Mode.RAIL).maxDailyHours()).isEqualTo(12.0);
        assertThat(registry.forMode(Shipment.Mode.AIR).maxDailyHours()).isEqualTo(13.0);
        assertThat(registry.forMode(Shipment.Mode.OCEAN).maxDailyHours()).isEqualTo(14.0);
        assertThat(registry.forMode(Shipment.Mode.ROAD).regulation()).contains("561");
        assertThat(registry.forMode(Shipment.Mode.OCEAN).regulation()).contains("STCW");
    }

    @Test
    void singleNonRoadModeUsesThatModesRules() {
        Carrier ocean = new Carrier();
        ocean.setSupportedModes(new HashSet<>(Set.of(Shipment.Mode.OCEAN)));
        assertThat(registry.forCarrier(ocean).maxDailyHours()).isEqualTo(14.0);
    }

    @Test
    void roadOnlyOrMultiModalUsesRoadBaseline() {
        Carrier empty = new Carrier();   // no supported modes = road-only
        assertThat(registry.forCarrier(empty).maxDailyHours()).isEqualTo(10.0);

        Carrier multi = new Carrier();
        multi.setSupportedModes(new HashSet<>(Set.of(Shipment.Mode.ROAD, Shipment.Mode.OCEAN)));
        assertThat(registry.forCarrier(multi).maxDailyHours()).isEqualTo(10.0);
    }
}
