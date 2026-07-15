package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Shipment;
import com.driverdirect.service.PricingPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The compiled rate card must price exactly like PricingService's
 * max(min, base + rate × quantity) with the same per-unit quantity
 * resolution — including the IATA volumetric-kg rule.
 */
class TariffTest {

    private static final CargoDetails NO_CARGO = new CargoDetails(null, null, null, null);

    @Test
    void fromCompilesTheAuthoritativeRateCard() {
        Tariff road = Tariff.from(new PricingPolicy().rateCardFor(Shipment.Mode.ROAD));
        assertThat(road.unit()).isEqualTo(ChargeUnit.PER_KM);
        assertThat(road.baseFee()).isEqualTo(50.0);
        assertThat(road.ratePerUnit()).isEqualTo(1.20);
        assertThat(road.minimumCharge()).isEqualTo(150.0);
    }

    @Test
    void perKmMetersDistance() {
        Tariff road = new Tariff(ChargeUnit.PER_KM, 50, 1.20, 150);
        assertThat(road.cost(NO_CARGO, 400)).isEqualTo(50 + 1.20 * 400);
        // Below the minimum: 50 + 1.20 × 50 = 110 → floor 150.
        assertThat(road.cost(NO_CARGO, 50)).isEqualTo(150);
        // Unknown distance → best-known floor.
        assertThat(road.cost(NO_CARGO, 0)).isEqualTo(150);
    }

    @Test
    void perContainerMetersContainerCount() {
        Tariff ocean = new Tariff(ChargeUnit.PER_CONTAINER, 350, 1800, 1800);
        assertThat(ocean.cost(new CargoDetails(null, null, 2, null), 0)).isEqualTo(350 + 3600);
        assertThat(ocean.cost(NO_CARGO, 0)).isEqualTo(1800); // no count → floor
    }

    @Test
    void perPieceMetersPieceCount() {
        Tariff parcel = new Tariff(ChargeUnit.PER_PIECE, 0, 8.50, 8.50);
        assertThat(parcel.cost(new CargoDetails(null, null, null, 4), 0)).isEqualTo(34.0);
    }

    @Test
    void chargeableKgIsMaxOfActualAndVolumetric() {
        Tariff air = new Tariff(ChargeUnit.PER_CHARGEABLE_KG, 0, 3.20, 75);
        // 2 m³ → 2,000,000 cm³ / 6000 = 333.33 volumetric kg > 100 actual kg.
        CargoDetails bulky = new CargoDetails(BigDecimal.valueOf(100), BigDecimal.valueOf(2), null, null);
        assertThat(air.cost(bulky, 0)).isCloseTo(3.20 * 333.333, within(0.1));
        // Dense cargo: actual wins.
        CargoDetails dense = new CargoDetails(BigDecimal.valueOf(400), BigDecimal.valueOf(0.5), null, null);
        assertThat(air.cost(dense, 0)).isCloseTo(3.20 * 400, within(0.001));
        // Tiny shipment → minimum charge.
        CargoDetails tiny = new CargoDetails(BigDecimal.TEN, null, null, null);
        assertThat(air.cost(tiny, 0)).isEqualTo(75);
    }
}
