package com.driverdirect.service;

import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-mode credential dispatch: road uses the licence lattice; air/sea/rail
 *  require a mode credential; intermodal/parcel are open. */
class CredentialMatcherRegistryTest {

    private final CredentialMatcherRegistry registry = new CredentialMatcherRegistry();

    @Test
    void roadUsesLicenceLattice() {
        // CLASS_A covers CLASS_C; not the reverse. Credentials are irrelevant for road.
        assertThat(registry.satisfies(Shipment.Mode.ROAD, "CLASS_A", Set.of(), "CLASS_C")).isTrue();
        assertThat(registry.satisfies(Shipment.Mode.ROAD, "CLASS_C", Set.of(), "CLASS_A")).isFalse();
    }

    @Test
    void airRequiresAnAirCredential() {
        assertThat(registry.satisfies(Shipment.Mode.AIR, "CLASS_A", Set.of("AIR:ATPL"), null)).isTrue();
        assertThat(registry.satisfies(Shipment.Mode.AIR, "CLASS_A", Set.of("OCEAN:STCW"), null)).isFalse();
        assertThat(registry.satisfies(Shipment.Mode.AIR, "CLASS_A", Set.of(), null)).isFalse();
        assertThat(registry.satisfies(Shipment.Mode.AIR, "CLASS_A", null, null)).isFalse();
    }

    @Test
    void oceanAndRailEachRequireTheirOwnCredential() {
        assertThat(registry.satisfies(Shipment.Mode.OCEAN, null, Set.of("OCEAN:STCW"), null)).isTrue();
        assertThat(registry.satisfies(Shipment.Mode.OCEAN, null, Set.of("AIR:ATPL"), null)).isFalse();
        assertThat(registry.satisfies(Shipment.Mode.RAIL, null, Set.of("RAIL:RUL"), null)).isTrue();
        assertThat(registry.satisfies(Shipment.Mode.RAIL, null, Set.of("OCEAN:STCW"), null)).isFalse();
    }

    @Test
    void intermodalAndParcelAreOpen() {
        assertThat(registry.satisfies(Shipment.Mode.INTERMODAL, null, Set.of(), null)).isTrue();
        assertThat(registry.satisfies(Shipment.Mode.PARCEL, null, Set.of(), null)).isTrue();
    }
}
