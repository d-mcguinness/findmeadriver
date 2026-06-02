package com.driverdirect.service;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Load;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The money path: per-mode rate-card basis, volumetric weight, min charge,
 *  hourly fallback, per-mode commission, and the itinerary roll-up. */
class PricingServiceTest {

    private final PricingPolicy policy = new PricingPolicy();
    private final ShipmentRepository shipmentRepo = mock(ShipmentRepository.class);
    private final ItineraryRepository itineraryRepo = mock(ItineraryRepository.class);
    private final PricingService pricing = new PricingService(policy, shipmentRepo, itineraryRepo);

    private Shipment leg(Shipment.Mode mode) {
        Shipment s = new Shipment();
        s.setId(1L);
        s.setMode(mode);
        s.setCurrency("EUR");
        return s;
    }

    private Load loadFor(Shipment s, String ratePerHour, double hours) {
        Load l = new Load();
        l.setShipment(s);
        l.setRatePerHour(new BigDecimal(ratePerHour));
        l.setEstimatedDurationHours(hours);
        return l;
    }

    private void price(Shipment s, Load l) {
        when(shipmentRepo.findById(1L)).thenReturn(Optional.of(s));
        pricing.priceLoad(l);
    }

    @Test
    void oceanPricedPerContainer() {
        Shipment s = leg(Shipment.Mode.OCEAN);
        s.setContainerCount(2);
        price(s, loadFor(s, "12", 8));
        assertThat(s.getChargeUnit()).isEqualTo(ChargeUnit.PER_CONTAINER);
        assertThat(s.getChargeableQuantity()).isEqualByComparingTo("2");
        assertThat(s.getTotalRate()).isEqualByComparingTo("3950");     // 350 + 1800×2
        assertThat(s.getCommissionPercent()).isEqualByComparingTo("15");
        assertThat(s.getCommissionAmount()).isEqualByComparingTo("592.50");
        assertThat(s.getShipperTotal()).isEqualByComparingTo("4542.50");
    }

    @Test
    void airUsesVolumetricWeightWhenItExceedsActual() {
        Shipment s = leg(Shipment.Mode.AIR);
        s.setWeightKg(new BigDecimal("80"));
        s.setVolumeM3(new BigDecimal("0.6"));   // 0.6 × 1e6 / 6000 = 100 kg > 80 kg actual
        price(s, loadFor(s, "80", 3));
        assertThat(s.getChargeUnit()).isEqualTo(ChargeUnit.PER_CHARGEABLE_KG);
        assertThat(s.getChargeableQuantity()).isEqualByComparingTo("100");
        assertThat(s.getTotalRate()).isEqualByComparingTo("320");      // 3.20 × 100
        assertThat(s.getShipperTotal()).isEqualByComparingTo("384");   // +20%
    }

    @Test
    void roadHitsMinimumCharge() {
        Shipment s = leg(Shipment.Mode.ROAD);
        s.setDistanceKm(new BigDecimal("10"));   // 50 + 1.20×10 = 62 < 150 minimum
        price(s, loadFor(s, "40", 2));
        assertThat(s.getTotalRate()).isEqualByComparingTo("150");
        assertThat(s.getShipperTotal()).isEqualByComparingTo("165");   // +10%
    }

    @Test
    void fallsBackToRateTimesHoursWhenNoQuantity() {
        Shipment s = leg(Shipment.Mode.ROAD);    // no distance → fallback
        price(s, loadFor(s, "40", 2));
        assertThat(s.getChargeUnit()).isEqualTo(ChargeUnit.PER_HOUR);
        assertThat(s.getTotalRate()).isEqualByComparingTo("80");       // 40 × 2
        assertThat(s.getShipperTotal()).isEqualByComparingTo("88");    // +10%
    }

    @Test
    void noOpWhenLoadHasNoShipment() {
        Load l = new Load();
        l.setRatePerHour(new BigDecimal("40"));
        l.setEstimatedDurationHours(2.0);
        pricing.priceLoad(l);   // must not throw
    }

    @Test
    void recalcItineraryRollsUpLegTotals() {
        Itinerary it = itinerary();
        when(shipmentRepo.findByItineraryOrderByLegSequenceAsc(it)).thenReturn(List.of(
                pricedLeg("EUR", "150", "15", "165"),
                pricedLeg("EUR", "3950", "592.50", "4542.50")));
        pricing.recalcItinerary(it);
        assertThat(it.getCarrierCostTotal()).isEqualByComparingTo("4100");
        assertThat(it.getCommissionTotal()).isEqualByComparingTo("607.50");
        assertThat(it.getGrandTotal()).isEqualByComparingTo("4707.50");
    }

    @Test
    void recalcItineraryRejectsMixedCurrency() {
        Itinerary it = itinerary();
        when(shipmentRepo.findByItineraryOrderByLegSequenceAsc(it)).thenReturn(List.of(
                pricedLeg("EUR", "150", "15", "165"),
                pricedLeg("USD", "100", "10", "110")));
        assertThatThrownBy(() -> pricing.recalcItinerary(it))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mixed-currency");
    }

    private Itinerary itinerary() {
        Itinerary it = new Itinerary();
        it.setId(7L);
        it.setCurrency("EUR");
        return it;
    }

    private Shipment pricedLeg(String currency, String carrier, String commission, String shipperTotal) {
        Shipment s = new Shipment();
        s.setCurrency(currency);
        s.setTotalRate(new BigDecimal(carrier));
        s.setCommissionAmount(new BigDecimal(commission));
        s.setShipperTotal(new BigDecimal(shipperTotal));
        return s;
    }
}
