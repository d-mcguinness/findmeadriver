package com.driverdirect.routing;

import com.driverdirect.model.CarrierLane;
import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.service.EmissionPolicy;
import com.driverdirect.service.PricingPolicy;
import com.driverdirect.service.TransferPolicy;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The graph build: timetabled, terminal-anchored lanes become scheduled
 * edges (zone resolved at build, UTC fallback); unusable lanes are skipped
 * fail-soft; and the snapshot closes over locations + road tariff and is
 * immutable — a search must never need a repository or the live policy.
 */
class RoutingGraphBuilderTest {

    private final CarrierLaneRepository laneRepository = mock(CarrierLaneRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final RoutingGraphBuilder builder = new RoutingGraphBuilder(
            laneRepository, locationRepository, new PricingPolicy(), new TransferPolicy(),
            new EmissionPolicy());

    private final Location dublinPort = location(10L, "Dublin Port", Location.LocationType.SEAPORT,
            "IE", 53.3498, -6.2603, "Europe/Dublin");
    private final Location rotterdam = location(20L, "Rotterdam Europoort", Location.LocationType.SEAPORT,
            "NL", 51.9496, 4.1453, "Europe/Amsterdam");
    private final Location addressStop = location(30L, "Acme DC", Location.LocationType.ADDRESS,
            "IE", 53.30, -6.30, null);

    private static Location location(Long id, String name, Location.LocationType type,
                                     String country, Double lat, Double lon, String timezone) {
        Location l = new Location();
        l.setId(id);
        l.setName(name);
        l.setLocationType(type);
        l.setCountry(country);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setTimezone(timezone);
        return l;
    }

    private CarrierLane oceanLane() {
        CarrierLane lane = new CarrierLane();
        lane.setId(1L);
        lane.setOriginCountry("IE");
        lane.setDestinationCountry("NL");
        lane.setServiceMode(Shipment.Mode.OCEAN);
        lane.setDepartureDays("MONDAY,THURSDAY");
        lane.setDepartureTime(LocalTime.of(8, 0));
        lane.setTransitDurationHours(36.0);
        lane.setOriginLocation(dublinPort);
        lane.setDestinationLocation(rotterdam);
        return lane;
    }

    private RoutingGraph buildWith(CarrierLane... lanes) {
        when(locationRepository.findAll()).thenReturn(List.of(dublinPort, rotterdam, addressStop));
        when(laneRepository.findTimetabledWithTerminals()).thenReturn(List.of(lanes));
        return builder.build();
    }

    @Test
    void timetabledLaneBecomesAScheduledEdge() {
        RoutingGraph graph = buildWith(oceanLane());

        assertThat(graph.edgesFrom(10L)).hasSize(1);
        ScheduledServiceEdge edge = (ScheduledServiceEdge) graph.edgesFrom(10L).get(0);
        assertThat(edge.destinationLocationId()).isEqualTo(20L);
        assertThat(edge.mode()).isEqualTo(Shipment.Mode.OCEAN);
        assertThat(edge.departureDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        assertThat(edge.departureTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(edge.zone()).isEqualTo(ZoneId.of("Europe/Dublin")); // origin's zone
        assertThat(edge.transitDuration()).isEqualTo(Duration.ofHours(36));
        assertThat(edge.rates().tariff().unit()).isEqualTo(ChargeUnit.PER_CONTAINER);
        assertThat(edge.rates().tariff().minimumCharge()).isEqualTo(1800.0);
        // OCEAN carbon factor compiled onto the edge (kg CO2e per tonne-km).
        assertThat(edge.rates().co2PerTonneKm()).isEqualTo(0.012);
        // Great-circle Dublin → Rotterdam ≈ 718 km.
        assertThat(edge.distanceKm()).isBetween(650.0, 780.0);
    }

    @Test
    void missingOrInvalidTimezoneFallsBackToUtc() {
        dublinPort.setTimezone(null);
        rotterdam.setTimezone("Not/AZone");
        RoutingGraph graph = buildWith(oceanLane());

        ScheduledServiceEdge edge = (ScheduledServiceEdge) graph.edgesFrom(10L).get(0);
        assertThat(edge.zone()).isEqualTo(ZoneOffset.UTC);
        assertThat(graph.location(20L).zone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void laneWithNoParseableDepartureDaysIsSkipped() {
        CarrierLane lane = oceanLane();
        lane.setDepartureDays("NODAY,ALSONOTADAY");
        RoutingGraph graph = buildWith(lane);
        assertThat(graph.edgesFrom(10L)).isEmpty();
    }

    @Test
    void laneWithBlankDepartureDaysIsSkipped() {
        // The repository query only filters null; blank slips through to the
        // Java-side isTimetabled() guard.
        CarrierLane lane = oceanLane();
        lane.setDepartureDays("  ");
        RoutingGraph graph = buildWith(lane);
        assertThat(graph.edgesFrom(10L)).isEmpty();
    }

    @Test
    void laneWhoseModeHasNoRateCardIsSkipped() {
        CarrierLane lane = oceanLane();
        lane.setServiceMode(null); // rateCardFor(null) → null
        RoutingGraph graph = buildWith(lane);
        assertThat(graph.edgesFrom(10L)).isEmpty();
    }

    @Test
    void laneWithUnusableTransitHoursIsSkippedNotFatal() {
        // API validation rejects these on write, but a stored row that
        // bypassed it must not take the whole build down (Duration overflow
        // on huge values, a zero-transit edge from NaN).
        for (double bad : new double[]{-5.0, Double.NaN, Double.POSITIVE_INFINITY, 1e17}) {
            CarrierLane lane = oceanLane();
            lane.setTransitDurationHours(bad);
            RoutingGraph graph = buildWith(lane);
            assertThat(graph.edgesFrom(10L)).as("transitDurationHours=" + bad).isEmpty();
        }
    }

    @Test
    void missingCoordinatesYieldUnknownDistanceNotACrash() {
        dublinPort.setLatitude(null);
        RoutingGraph graph = buildWith(oceanLane());
        ScheduledServiceEdge edge = (ScheduledServiceEdge) graph.edgesFrom(10L).get(0);
        assertThat(edge.distanceKm()).isEqualTo(0.0);
    }

    @Test
    void snapshotClosesOverLocationsAndRoadTariff() {
        RoutingGraph graph = buildWith(oceanLane());

        assertThat(graph.locations()).containsKeys(10L, 20L);
        assertThat(graph.location(10L).name()).isEqualTo("Dublin Port");
        assertThat(graph.location(10L).hasCoordinates()).isTrue();
        // Road rates ride along so virtual RoadEdge generation never touches
        // the live PricingPolicy/EmissionPolicy mid-search.
        assertThat(graph.roadRates().tariff().unit()).isEqualTo(ChargeUnit.PER_KM);
        assertThat(graph.roadRates().tariff().minimumCharge()).isEqualTo(150.0);
        assertThat(graph.roadRates().co2PerTonneKm()).isEqualTo(0.075);
    }

    @Test
    void typedTerminalsGetDefaultTransferProfiles() {
        // No per-location transfer table yet — TransferPolicy code defaults
        // are compiled into the snapshot so mode changes are possible at all.
        RoutingGraph graph = buildWith(oceanLane());
        assertThat(graph.transferProfile(10L, Shipment.Mode.ROAD, Shipment.Mode.OCEAN)).isNotNull();
        // Cost/dwell = the harder side's handling (OCEAN: 150 / 360 min).
        assertThat(graph.transferProfile(10L, Shipment.Mode.OCEAN, Shipment.Mode.ROAD).cost())
                .isEqualTo(150.0);
        assertThat(graph.transferProfile(10L, Shipment.Mode.RAIL, Shipment.Mode.OCEAN)).isNotNull();
        // Same-mode scheduled interchange (vessel→vessel) is costed too...
        assertThat(graph.transferProfile(10L, Shipment.Mode.OCEAN, Shipment.Mode.OCEAN)).isNotNull();
        // ...but road→road self-transfer is not (one truck driving on).
        assertThat(graph.transferProfile(10L, Shipment.Mode.ROAD, Shipment.Mode.ROAD)).isNull();
        // A seaport doesn't handle air.
        assertThat(graph.transferProfile(10L, Shipment.Mode.ROAD, Shipment.Mode.AIR)).isNull();
        // A plain address transfers nothing.
        assertThat(graph.transferProfile(30L, Shipment.Mode.ROAD, Shipment.Mode.OCEAN)).isNull();
    }

    @Test
    void snapshotIsImmutable() {
        RoutingGraph graph = buildWith(oceanLane());
        assertThatThrownBy(() -> graph.edgesByOriginLocation().put(99L, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.edgesFrom(10L).add(graph.edgesFrom(10L).get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.locations().remove(10L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordDeepFreezesHandBuiltGraphsToo() {
        // The immutability guarantee lives in the record's compact
        // constructor, not in the builder: mutable inputs are frozen.
        LegRates road = new LegRates(new Tariff(ChargeUnit.PER_KM, 50, 1.20, 150), 0.075, 0);
        ServiceEdge edge = new RoadEdge(1L, 2L, 100, 70, road);
        RoutingGraph graph = new RoutingGraph(
                new HashMap<>(Map.of(1L, new ArrayList<>(List.of(edge)))),
                Map.of(), Map.of(), road);
        assertThatThrownBy(() -> graph.edgesFrom(1L).add(edge))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
