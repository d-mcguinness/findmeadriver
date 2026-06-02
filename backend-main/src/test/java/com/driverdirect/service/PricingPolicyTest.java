package com.driverdirect.service;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Shipment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPolicyTest {

    private final PricingPolicy policy = new PricingPolicy();

    @Test
    void commissionVariesByMode() {
        assertThat(policy.commissionPercentFor(Shipment.Mode.ROAD)).isEqualByComparingTo("10");
        assertThat(policy.commissionPercentFor(Shipment.Mode.RAIL)).isEqualByComparingTo("12");
        assertThat(policy.commissionPercentFor(Shipment.Mode.OCEAN)).isEqualByComparingTo("15");
        assertThat(policy.commissionPercentFor(Shipment.Mode.AIR)).isEqualByComparingTo("20");
    }

    @Test
    void nullModeDefaultsToRoadCommission() {
        assertThat(policy.commissionPercentFor(null)).isEqualByComparingTo("10");
    }

    @Test
    void rateCardUnitMatchesMode() {
        assertThat(policy.rateCardFor(Shipment.Mode.ROAD).unit()).isEqualTo(ChargeUnit.PER_KM);
        assertThat(policy.rateCardFor(Shipment.Mode.OCEAN).unit()).isEqualTo(ChargeUnit.PER_CONTAINER);
        assertThat(policy.rateCardFor(Shipment.Mode.RAIL).unit()).isEqualTo(ChargeUnit.PER_CONTAINER);
        assertThat(policy.rateCardFor(Shipment.Mode.AIR).unit()).isEqualTo(ChargeUnit.PER_CHARGEABLE_KG);
    }

    @Test
    void roadHasAMinimumCharge() {
        assertThat(policy.rateCardFor(Shipment.Mode.ROAD).minimumCharge()).isEqualByComparingTo("150");
    }

    @Test
    void airVolumetricDivisorIsIataStandard() {
        assertThat(PricingPolicy.AIR_VOLUMETRIC_DIVISOR).isEqualByComparingTo("6000");
    }
}
