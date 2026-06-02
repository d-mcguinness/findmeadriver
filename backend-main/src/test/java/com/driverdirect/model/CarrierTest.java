package com.driverdirect.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The mode-capability rule: an empty supported-modes set means road-only. */
class CarrierTest {

    @Test
    void emptyOrNullMeansRoadOnly() {
        assertThat(Carrier.supportsMode(null, Shipment.Mode.ROAD)).isTrue();
        assertThat(Carrier.supportsMode(Set.of(), Shipment.Mode.ROAD)).isTrue();
        assertThat(Carrier.supportsMode(Set.of(), Shipment.Mode.OCEAN)).isFalse();
    }

    @Test
    void explicitModesGateExactly() {
        Set<Shipment.Mode> modes = Set.of(Shipment.Mode.OCEAN, Shipment.Mode.AIR);
        assertThat(Carrier.supportsMode(modes, Shipment.Mode.OCEAN)).isTrue();
        assertThat(Carrier.supportsMode(modes, Shipment.Mode.AIR)).isTrue();
        assertThat(Carrier.supportsMode(modes, Shipment.Mode.ROAD)).isFalse();   // not declared
        assertThat(Carrier.supportsMode(modes, Shipment.Mode.RAIL)).isFalse();
    }

    @Test
    void instanceMethodReadsTheCarriersModes() {
        Carrier c = new Carrier();
        c.setSupportedModes(new HashSet<>(Set.of(Shipment.Mode.AIR)));
        assertThat(c.supportsMode(Shipment.Mode.AIR)).isTrue();
        assertThat(c.supportsMode(Shipment.Mode.ROAD)).isFalse();
    }
}
