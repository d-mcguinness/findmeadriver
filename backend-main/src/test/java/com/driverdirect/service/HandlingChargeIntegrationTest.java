package com.driverdirect.service;

import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLegRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipper;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipperRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Terminal handling as a chargeable item: one {@link com.driverdirect.model.HandlingCharge}
 * per interchange, derived from {@link TransferPolicy} by
 * {@code PricingService.recalcItinerary} and rolled into the itinerary's
 * grandTotal.
 *
 * <p>The rules under test mirror {@code RoutePlanner.relax}'s transfer
 * condition, which is what makes a route quote reconcile with its bill: a mode
 * change or boarding a scheduled service is charged; road→road is one truck
 * driving on and is free; and a terminal that can't handle both modes charges
 * nothing, because no interchange happens there.
 */
@SpringBootTest
class HandlingChargeIntegrationTest {

    @Autowired
    private LoadService loadService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ShipperRepository shipperRepository;
    @Autowired
    private ItineraryRepository itineraryRepository;

    private Long locationId(String name, String country) {
        return locationRepository.findFirstByNameIgnoreCaseAndCountry(name, country)
                .map(Location::getId)
                .orElseThrow(() -> new AssertionError("seed missing location: " + name));
    }

    private Shipper acme() {
        return shipperRepository.findByEmail("employer@company.com").orElseThrow();
    }

    private CreateLegRequest leg(String mode, Long from, Long to) {
        CreateLegRequest l = new CreateLegRequest();
        l.setTransportMode(mode);
        l.setPickupLocationId(from);
        l.setDeliveryLocationId(to);
        l.setDistanceKm(BigDecimal.valueOf(120));
        l.setContainerCount(1);
        l.setWeightKg(BigDecimal.valueOf(12000));
        return l;
    }

    /** A leg addressed by name — the shared middle name upserts to a single
     *  ad-hoc ADDRESS row, so consecutive legs genuinely meet there. */
    private CreateLegRequest namedLeg(String mode, String from, String to) {
        CreateLegRequest l = new CreateLegRequest();
        l.setTransportMode(mode);
        l.setPickupLocation(from);
        l.setPickupCountry("IE");
        l.setDeliveryLocation(to);
        l.setDeliveryCountry("IE");
        l.setDistanceKm(BigDecimal.valueOf(120));
        return l;
    }

    /** Pickup by name, delivery by id — the realistic shape: a shipper's own
     *  origin address isn't a public reference node it may cite by id (the
     *  tenant rule rejects that), while a terminal is. */
    private CreateLegRequest legFromNameToId(String mode, String fromName, Long toId) {
        CreateLegRequest l = leg(mode, null, toId);
        l.setPickupLocation(fromName);
        l.setPickupCountry("IE");
        return l;
    }

    private CreateIntermodalLoadRequest request(String title, CreateLegRequest... legs) {
        CreateIntermodalLoadRequest r = new CreateIntermodalLoadRequest();
        r.setTitle(title);
        r.setDateNeeded(LocalDate.now().plusDays(3));
        r.setLegs(List.of(legs));
        return r;
    }

    @Test
    @Transactional
    void aModeChangeAtATerminalIsChargedAndLandsInTheGrandTotal() {
        Long dublinPort = locationId("Dublin Port", "IE"); // SEAPORT
        Long leHavre = locationId("Le Havre", "FR");       // SEAPORT

        ItineraryResponse it = loadService.createIntermodalLoad(acme(), request(
                "Road feeder then sea",
                legFromNameToId("ROAD", "Shipper Yard Cork", dublinPort),
                leg("OCEAN", dublinPort, leHavre)));

        // One interchange: ROAD→OCEAN at Dublin Port, priced at the harder
        // side's handling (max(road 50, ocean 150)).
        assertThat(it.getHandling()).singleElement().satisfies(h -> {
            assertThat(h.getFromMode()).isEqualTo("ROAD");
            assertThat(h.getToMode()).isEqualTo("OCEAN");
            assertThat(h.getLocationName()).isEqualTo("Dublin Port");
            assertThat(h.getAmount()).isEqualByComparingTo("150.00");
            assertThat(h.getAfterLegSequence()).isEqualTo(1);
        });
        assertThat(it.getHandlingTotal()).isEqualByComparingTo("150.00");
        // And it is part of what the shipper pays, not a side note.
        assertThat(it.getGrandTotal()).isEqualByComparingTo(
                it.getCarrierCostTotal().add(it.getCommissionTotal()).add(it.getHandlingTotal()));
    }

    @Test
    @Transactional
    void roadToRoadIsNotCharged() {
        // One truck driving on through an intermediate point: nothing is
        // handled, so nothing is charged. Also the ADDRESS case — a street
        // address can't interchange cargo at all.
        ItineraryResponse it = loadService.createIntermodalLoad(acme(), request(
                "Road all the way",
                namedLeg("ROAD", "Cork City", "Limerick Depot"),
                namedLeg("ROAD", "Limerick Depot", "Galway Depot")));

        assertThat(it.getHandling()).isEmpty();
        assertThat(it.getHandlingTotal()).isEqualByComparingTo("0.00");
        assertThat(it.getGrandTotal()).isEqualByComparingTo(
                it.getCarrierCostTotal().add(it.getCommissionTotal()));
    }

    @Test
    @Transactional
    void aSameModeTransshipmentAtASharedHubIsCharged() {
        // Two sailings meeting at a port is real transshipment — cargo comes off
        // one vessel and onto another — so it is charged even though the mode
        // never changes. Matches the planner, which requires a transfer profile
        // for a scheduled-to-scheduled interchange too.
        Long dublinPort = locationId("Dublin Port", "IE");
        Long leHavre = locationId("Le Havre", "FR");
        Long rotterdam = locationId("Port of Rotterdam", "NL");

        ItineraryResponse it = loadService.createIntermodalLoad(acme(), request(
                "Sea then sea",
                leg("OCEAN", dublinPort, leHavre),
                leg("OCEAN", leHavre, rotterdam)));

        assertThat(it.getHandling()).singleElement().satisfies(h -> {
            assertThat(h.getFromMode()).isEqualTo("OCEAN");
            assertThat(h.getToMode()).isEqualTo("OCEAN");
            assertThat(h.getLocationName()).isEqualTo("Le Havre");
            assertThat(h.getAmount()).isEqualByComparingTo("150.00");
        });
    }

    @Test
    @Transactional
    void recalculatingDoesNotDoubleChargeAnInterchange() {
        Long dublinPort = locationId("Dublin Port", "IE");
        Long leHavre = locationId("Le Havre", "FR");

        CreateIntermodalLoadRequest req = request("Re-edited",
                legFromNameToId("ROAD", "Shipper Yard Cork", dublinPort),
                leg("OCEAN", dublinPort, leHavre));
        Long id = loadService.createIntermodalLoad(acme(), req).getId();

        // Editing re-prices, which re-derives handling. The charges are replaced
        // wholesale, so an edit must not stack a second set on top.
        req.setTitle("Re-edited twice");
        loadService.updateIntermodalLoad(id, acme(), req);
        ItineraryResponse after = loadService.updateIntermodalLoad(id, acme(), req);

        assertThat(after.getHandling()).hasSize(1);
        assertThat(after.getHandlingTotal()).isEqualByComparingTo("150.00");
        assertThat(itineraryRepository.findById(id).orElseThrow().getHandlingCharges())
                .as("persisted rows, not just the response view")
                .hasSize(1);
    }
}
