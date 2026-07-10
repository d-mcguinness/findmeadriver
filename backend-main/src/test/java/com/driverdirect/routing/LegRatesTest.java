package com.driverdirect.routing;

import com.driverdirect.model.ChargeUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The bundled cost + emission rates an edge carries. Cost delegates to
 * {@link Tariff}; CO2 is distance × tonnes × factor, zero when the distance
 * or weight is unknown.
 */
class LegRatesTest {

    private final LegRates ocean =
            new LegRates(new Tariff(ChargeUnit.PER_CONTAINER, 350, 1800, 1800), 0.012);

    @Test
    void co2IsDistanceByTonnesByFactor() {
        CargoDetails twentyTonnes = new CargoDetails(BigDecimal.valueOf(20000), null, 1, null);
        assertThat(ocean.co2(twentyTonnes, 800)).isCloseTo(800 * 20 * 0.012, within(0.001));
    }

    @Test
    void co2IsZeroWithoutWeightOrDistance() {
        CargoDetails noWeight = new CargoDetails(null, null, 1, null);
        assertThat(ocean.co2(noWeight, 800)).isZero();
        CargoDetails weighed = new CargoDetails(BigDecimal.valueOf(20000), null, 1, null);
        assertThat(ocean.co2(weighed, 0)).isZero();
    }

    @Test
    void costDelegatesToTariff() {
        CargoDetails twoContainers = new CargoDetails(null, null, 2, null);
        assertThat(ocean.cost(twoContainers, 0)).isEqualTo(350 + 1800 * 2);
    }
}
