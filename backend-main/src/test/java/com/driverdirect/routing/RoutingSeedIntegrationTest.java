package com.driverdirect.routing;

import com.driverdirect.dto.AcceptRouteRequest;
import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.RouteOptionResponse;
import com.driverdirect.dto.RouteQueryRequest;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Shipper;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.service.LoadService;
import com.driverdirect.service.RoutePlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that the seeded graph (typed terminals with coordinates +
 * timezones from tier 1, competing continental services from tier 2)
 * actually exercises the Pareto search: a Dublin→Paris query must return a
 * multi-option (cost, CO2) front including a multi-leg intermodal option —
 * not the single thin result the pre-tier seed produced.
 *
 * <p>Boots the full context so DataInitializer seeds H2 and
 * RoutePlannerService builds the graph from real rows, exactly as a future
 * REST endpoint would.
 */
@SpringBootTest
class RoutingSeedIntegrationTest {

    @Autowired
    private RoutePlannerService routePlannerService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ShipperRepository shipperRepository;
    @Autowired
    private LoadService loadService;

    private Long locationId(String name, String country) {
        return locationRepository.findFirstByNameIgnoreCaseAndCountry(name, country)
                .map(Location::getId)
                .orElseThrow(() -> new AssertionError("seed missing location: " + name + " " + country));
    }

    @Test
    void seededGraphYieldsAMultiModalParetoFrontDublinToParis() {
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");
        // 15 tonnes so the CO2 axis is real (emissions scale with weight).
        CargoDetails cargo = new CargoDetails(BigDecimal.valueOf(15000), null, 1, null);

        List<RouteOption> options = routePlannerService.findOptions(new RouteQuery(
                dublinPort, parisCdg, cargo,
                LocalDate.now().plusDays(1), null, null));

        // A genuine front, not one thin option.
        assertThat(options).hasSizeGreaterThanOrEqualTo(2);
        // Returned cost-ascending.
        assertThat(options).isSortedAccordingTo(Comparator.comparingDouble(RouteOption::totalCost));
        // At least one option is multi-leg (a road feeder + a scheduled leg,
        // or a full sea→rail→road intermodal chain).
        assertThat(options).anySatisfy(o -> assertThat(o.legs()).hasSizeGreaterThan(1));
        // At least one option moves by a scheduled mode (sea/rail/air), i.e.
        // the graph's services are reachable and priced, not just road.
        assertThat(options).anySatisfy(o -> assertThat(o.legs())
                .extracting(ServiceEdge::mode)
                .anyMatch(m -> m != Shipment.Mode.ROAD));
        // The CO2 axis is populated (coordinates → real leg distances).
        assertThat(options).anySatisfy(o -> assertThat(o.totalCo2()).isGreaterThan(0.0));
    }

    @Test
    void theReturnedFrontIsAGenuineCostVsCo2Tradeoff() {
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");
        CargoDetails cargo = new CargoDetails(BigDecimal.valueOf(15000), null, 1, null);

        List<RouteOption> options = routePlannerService.findOptions(new RouteQuery(
                dublinPort, parisCdg, cargo,
                LocalDate.now().plusDays(1), null, null));

        // A (cost, CO2) Pareto front with more than one point must strictly
        // trade off: as cost rises across the cost-ascending front, CO2 must
        // strictly fall — otherwise the pricier option would be dominated and
        // never returned. This is the property that makes the multi-option
        // front meaningful (a single greenest-and-cheapest option would just
        // dominate everything and collapse to one). Only the cheap-dirty
        // direct road and the pricier-greener sea+road option survive here;
        // the sea→rail intermodal chain, though a valid route, is dominated on
        // BOTH axes by the shorter sea route to Le Havre and correctly omitted.
        assertThat(options).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 1; i < options.size(); i++) {
            assertThat(options.get(i).totalCo2())
                    .as("option %d greener than the cheaper option %d", i, i - 1)
                    .isLessThan(options.get(i - 1).totalCo2());
        }
    }

    @Test
    void planRoutesMapsToResponseDtosWithResolvedLegNames() {
        // The API-facing path (what the controllers call): request DTO in,
        // response DTOs out, with leg location names resolved from the graph.
        RouteQueryRequest request = new RouteQueryRequest();
        request.setOriginLocationId(locationId("Dublin Port", "IE"));
        request.setDestinationLocationId(locationId("Paris Charles de Gaulle", "FR"));
        request.setWeightKg(BigDecimal.valueOf(15000));
        request.setContainerCount(1);
        request.setEarliestReady(LocalDate.now().plusDays(1));

        List<RouteOptionResponse> options = routePlannerService.planRoutes(request.toQuery());

        assertThat(options).hasSizeGreaterThanOrEqualTo(2);
        assertThat(options).allSatisfy(o -> {
            assertThat(o.getLegs()).isNotEmpty();
            assertThat(o.getTotalCost()).isPositive();
            assertThat(o.getArrival()).isNotNull();
            // Every leg carries a resolved mode and endpoint names.
            assertThat(o.getLegs()).allSatisfy(leg -> {
                assertThat(leg.getMode()).isNotBlank();
                assertThat(leg.getOriginLocationName()).isNotBlank();
                assertThat(leg.getDestinationLocationName()).isNotBlank();
            });
        });
        // At least one option includes a scheduled (timetabled sea/rail/air)
        // leg — i.e. the graph's services are reachable through the DTO path.
        assertThat(options).anySatisfy(o ->
                assertThat(o.getLegs()).anyMatch(com.driverdirect.dto.RouteLegResponse::isScheduled));
    }

    @Test
    void shipperScopingPermitsReferenceNodesButRejectsAnotherTenantsAddress() {
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");     // SEAPORT — public
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR"); // AIRPORT — public
        CargoDetails cargo = new CargoDetails(BigDecimal.valueOf(15000), null, 1, null);

        // Public reference nodes: allowed.
        assertThat(routePlannerService.planRoutesForShipper(
                new RouteQuery(dublinPort, parisCdg, cargo, LocalDate.now().plusDays(1), null, null), acme))
                .isNotEmpty();

        // An ad-hoc ADDRESS location created from another load (owner null,
        // not the caller's) must be rejected exactly like an unknown id — no
        // name resolved or leaked. "Cork City" is a seeded load delivery stop.
        Long othersAddress = locationId("Cork City", "IE");
        assertThat(othersAddress).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                routePlannerService.planRoutesForShipper(
                        new RouteQuery(othersAddress, parisCdg, cargo,
                                LocalDate.now().plusDays(1), null, null), acme))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown location");
    }

    @Test
    void acceptingAProposedRouteCreatesAPlannedItinerary() {
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");

        RouteQueryRequest q = new RouteQueryRequest();
        q.setOriginLocationId(dublinPort);
        q.setDestinationLocationId(parisCdg);
        q.setWeightKg(BigDecimal.valueOf(15000));
        q.setContainerCount(1);
        q.setEarliestReady(LocalDate.now().plusDays(1));
        List<RouteOptionResponse> options = routePlannerService.planRoutesForShipper(q.toQuery(), acme);

        // Accept the multi-leg (sea + last-mile road) option.
        RouteOptionResponse chosen = options.stream()
                .filter(o -> o.getLegs().size() > 1)
                .findFirst().orElseThrow();

        AcceptRouteRequest accept = new AcceptRouteRequest();
        accept.setOriginLocationId(dublinPort);
        accept.setDestinationLocationId(parisCdg);
        accept.setWeightKg(BigDecimal.valueOf(15000));
        accept.setContainerCount(1);
        accept.setEarliestReady(LocalDate.now().plusDays(1));
        accept.setLegs(chosen.getLegs().stream().map(l -> {
            AcceptRouteRequest.AcceptedLeg al = new AcceptRouteRequest.AcceptedLeg();
            al.setOriginLocationId(l.getOriginLocationId());
            al.setDestinationLocationId(l.getDestinationLocationId());
            al.setMode(l.getMode());
            return al;
        }).toList());

        CreateIntermodalLoadRequest req = routePlannerService.buildAcceptedItinerary(accept, acme);
        ItineraryResponse itinerary = loadService.createIntermodalLoad(acme, req);

        List<String> expectedModes = chosen.getLegs().stream()
                .map(com.driverdirect.dto.RouteLegResponse::getMode).toList();
        assertThat(itinerary.getStatus()).isEqualTo("PLANNED");
        assertThat(itinerary.getLegCount()).isEqualTo(chosen.getLegs().size());
        assertThat(itinerary.getMode()).isEqualTo("INTERMODAL"); // spans >1 mode
        assertThat(itinerary.getLegs()).extracting(l -> l.getMode())
                .containsExactlyElementsOf(expectedModes);
        assertThat(itinerary.getGrandTotal()).isGreaterThan(BigDecimal.ZERO);
        // Persisted and visible to the owner.
        assertThat(loadService.getItinerariesByShipper(acme))
                .anyMatch(i -> i.getId().equals(itinerary.getId()));
    }

    @Test
    void acceptingARouteTheEngineDidNotProposeIsRejected() {
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");

        // A single direct AIR leg Dublin Port → CDG is not an edge the engine
        // produces (air departs from Cork), so no option matches this selector.
        AcceptRouteRequest accept = new AcceptRouteRequest();
        accept.setOriginLocationId(dublinPort);
        accept.setDestinationLocationId(parisCdg);
        accept.setWeightKg(BigDecimal.valueOf(15000));
        accept.setContainerCount(1);
        accept.setEarliestReady(LocalDate.now().plusDays(1));
        AcceptRouteRequest.AcceptedLeg fake = new AcceptRouteRequest.AcceptedLeg();
        fake.setOriginLocationId(dublinPort);
        fake.setDestinationLocationId(parisCdg);
        fake.setMode("AIR");
        accept.setLegs(List.of(fake));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                routePlannerService.buildAcceptedItinerary(accept, acme))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void acceptingASeaRouteWithoutAContainerCountIsRejectedNotZeroPriced() {
        // The estimate floors a container leg to the card minimum even with
        // weight-only cargo, but booking creates a real priced Load — so
        // acceptance must demand the container count rather than post a €0 sea
        // leg. Regression guard for the estimate-vs-reprice divergence.
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");

        RouteQueryRequest q = new RouteQueryRequest();
        q.setOriginLocationId(dublinPort);
        q.setDestinationLocationId(parisCdg);
        q.setWeightKg(BigDecimal.valueOf(15000)); // weight only — no containerCount
        q.setEarliestReady(LocalDate.now().plusDays(1));
        List<RouteOptionResponse> options = routePlannerService.planRoutesForShipper(q.toQuery(), acme);
        RouteOptionResponse seaOption = options.stream()
                .filter(o -> o.getLegs().stream().anyMatch(l -> "OCEAN".equals(l.getMode())))
                .findFirst().orElseThrow();

        AcceptRouteRequest accept = new AcceptRouteRequest();
        accept.setOriginLocationId(dublinPort);
        accept.setDestinationLocationId(parisCdg);
        accept.setWeightKg(BigDecimal.valueOf(15000)); // still no containerCount
        accept.setEarliestReady(LocalDate.now().plusDays(1));
        accept.setLegs(seaOption.getLegs().stream().map(l -> {
            AcceptRouteRequest.AcceptedLeg al = new AcceptRouteRequest.AcceptedLeg();
            al.setOriginLocationId(l.getOriginLocationId());
            al.setDestinationLocationId(l.getDestinationLocationId());
            al.setMode(l.getMode());
            return al;
        }).toList());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                routePlannerService.buildAcceptedItinerary(accept, acme))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per container");
    }
}
