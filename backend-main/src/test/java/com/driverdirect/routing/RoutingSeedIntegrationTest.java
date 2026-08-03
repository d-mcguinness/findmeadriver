package com.driverdirect.routing;

import com.driverdirect.dto.AcceptRouteRequest;
import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLegRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.RouteLegResponse;
import com.driverdirect.dto.RouteOptionResponse;
import com.driverdirect.dto.RouteQueryRequest;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Stop;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.repository.StopRepository;
import com.driverdirect.service.LoadService;
import com.driverdirect.service.RoutePlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private ItineraryRepository itineraryRepository;
    @Autowired
    private StopRepository stopRepository;

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
    @Transactional
    void acceptedLegStopsBindToThePlannedLocationRowsNotNameLookalikes() {
        // The planner routes over Location ids; the booked legs must land on
        // those exact rows. Before endpoint ids were carried on CreateLegRequest
        // the acceptance path handed the tree name+country strings, which
        // TmsTreeService re-resolved via findFirstByNameIgnoreCaseAndCountry —
        // a lossy round-trip that on a miss mints a duplicate, untyped,
        // coordinate-less ADDRESS the next graph build sees as a different node.
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");
        Long parisCdg = locationId("Paris Charles de Gaulle", "FR");

        RouteQueryRequest q = new RouteQueryRequest();
        q.setOriginLocationId(dublinPort);
        q.setDestinationLocationId(parisCdg);
        q.setWeightKg(BigDecimal.valueOf(15000));
        q.setContainerCount(1);
        q.setEarliestReady(LocalDate.now().plusDays(1));
        RouteOptionResponse chosen = routePlannerService.planRoutesForShipper(q.toQuery(), acme).stream()
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
        // Every leg carries its endpoint ids, and they are the planned ones.
        assertThat(req.getLegs()).extracting(CreateLegRequest::getPickupLocationId)
                .containsExactlyElementsOf(chosen.getLegs().stream()
                        .map(RouteLegResponse::getOriginLocationId).toList());
        assertThat(req.getLegs()).extracting(CreateLegRequest::getDeliveryLocationId)
                .containsExactlyElementsOf(chosen.getLegs().stream()
                        .map(RouteLegResponse::getDestinationLocationId).toList());

        long locationsBefore = locationRepository.count();
        ItineraryResponse response = loadService.createIntermodalLoad(acme, req);

        // Booking an already-known route invents no new Location rows.
        assertThat(locationRepository.count())
                .as("accepting a planned route must reuse existing Location rows")
                .isEqualTo(locationsBefore);

        // Each persisted leg's PICKUP/DELIVERY Stop points at the planned row.
        List<Shipment> legs = itineraryRepository.findById(response.getId()).orElseThrow().getLegs();
        assertThat(legs).hasSameSizeAs(chosen.getLegs());
        for (int i = 0; i < legs.size(); i++) {
            RouteLegResponse planned = chosen.getLegs().get(i);
            List<Stop> stops = stopRepository.findByShipmentOrderBySequenceAsc(legs.get(i));
            assertThat(stops).as("leg %d stops", i).hasSize(2);
            assertThat(stops.get(0).getLocation().getId()).isEqualTo(planned.getOriginLocationId());
            assertThat(stops.get(1).getLocation().getId()).isEqualTo(planned.getDestinationLocationId());
            // ...and it is still the typed terminal, not a flattened ADDRESS.
            assertThat(stops.get(0).getLocation().getName()).isEqualTo(planned.getOriginLocationName());
            assertThat(stops.get(1).getLocation().getName()).isEqualTo(planned.getDestinationLocationName());
        }
    }

    @Test
    @Transactional
    void anEndpointIdBeatsAConflictingNameAndSuppliesTheCountry() {
        // The sharp edge of the old name round-trip, isolated: when the id and
        // the name disagree, the name path would have upserted a brand-new
        // "Nowhere"/FR ADDRESS and bound the Stop to that. The id must win, and
        // the resolved row's own country must stand in when none is sent.
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();
        Long dublinPort = locationId("Dublin Port", "IE");
        Long corkAirport = locationId("Cork Airport", "IE");

        CreateLegRequest leg = new CreateLegRequest();
        leg.setTransportMode("ROAD");
        leg.setPickupLocationId(dublinPort);
        leg.setPickupLocation("Nowhere");      // deliberately wrong
        leg.setPickupCountry("FR");            // deliberately wrong
        leg.setDeliveryLocationId(corkAirport);
        leg.setDeliveryLocation(null);         // id only — no name, no country
        leg.setDistanceKm(BigDecimal.valueOf(250));

        CreateIntermodalLoadRequest req = new CreateIntermodalLoadRequest();
        req.setTitle("Id-addressed leg");
        req.setDateNeeded(LocalDate.now().plusDays(2));
        req.setLegs(List.of(leg));

        long locationsBefore = locationRepository.count();
        ItineraryResponse response = loadService.createIntermodalLoad(acme, req);

        assertThat(locationRepository.count())
                .as("the conflicting name must not mint a Location")
                .isEqualTo(locationsBefore);

        Shipment persisted = itineraryRepository.findById(response.getId()).orElseThrow().getLegs().get(0);
        List<Stop> stops = stopRepository.findByShipmentOrderBySequenceAsc(persisted);
        assertThat(stops.get(0).getLocation().getId()).isEqualTo(dublinPort);
        assertThat(stops.get(0).getLocation().getName()).isEqualTo("Dublin Port");
        assertThat(stops.get(1).getLocation().getId()).isEqualTo(corkAirport);
        // Country: the caller's when given (wrong or not — it is theirs to
        // state), the resolved row's when omitted.
        assertThat(persisted.getOriginCountry()).isEqualTo("FR");
        assertThat(persisted.getDestinationCountry()).isEqualTo("IE");
    }

    @Test
    void anEndpointIdTheShipperMayNotReferenceIsRejectedLikeAnUnknownOne() {
        // The endpoint ids are reachable from the public itinerary POST, not
        // only from an accepted route, so they carry the same tenant scoping
        // the route planner applies — and fail identically, leaking nothing.
        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();

        CreateLegRequest leg = new CreateLegRequest();
        leg.setTransportMode("ROAD");
        leg.setPickupLocationId(locationId("Cork City", "IE")); // ad-hoc ADDRESS, not acme's
        leg.setDeliveryLocationId(locationId("Dublin Port", "IE"));
        leg.setDistanceKm(BigDecimal.valueOf(250));

        CreateIntermodalLoadRequest req = new CreateIntermodalLoadRequest();
        req.setTitle("Should not be created");
        req.setDateNeeded(LocalDate.now().plusDays(2));
        req.setLegs(List.of(leg));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                loadService.createIntermodalLoad(acme, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown location");
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
