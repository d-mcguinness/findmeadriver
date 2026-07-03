package com.driverdirect.service;

import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLoadRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.LoadResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.LoadApplicationRepository;
import com.driverdirect.repository.LoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoadServiceImpl implements LoadService {

    private final LoadRepository loadRepository;
    private final LoadApplicationRepository applicationRepository;
    private final AvailabilityService availabilityService;
    private final TmsTreeService tmsTreeService;
    private final CarrierLaneService carrierLaneService;
    private final CabotageService cabotageService;
    private final PricingService pricingService;
    private final CredentialMatcherRegistry credentialMatchers;
    private final ItineraryRepository itineraryRepository;

    @Override
    @Transactional
    public LoadResponse createLoad(Shipper shipper, CreateLoadRequest request) {
        // Load-level fields only — customer-facing metadata lives on the tree.
        Load load = new Load();
        load.setShipper(shipper);
        load.setEstimatedDurationHours(request.getEstimatedDurationHours());
        load.setRatePerHour(request.getRatePerHour());
        load.setRequiredLicenceCategory(request.getRequiredLicenceCategory());
        String currency = request.getCurrency() != null
                ? request.getCurrency()
                : (shipper.getCurrency() != null ? shipper.getCurrency() : "EUR");
        load.setCurrency(currency);
        load.setStatus(LoadStatus.OPEN);
        load = loadRepository.save(load);

        // Compose the TMS tree around the bare Load. Country defaults inherit
        // from the shipper unless the request explicitly overrides.
        tmsTreeService.createTreeFor(load, TmsTreeService.TmsOrderInput.fromRequest(request, shipper));
        load = loadRepository.save(load);

        // M3b: carry the per-mode pricing quantities onto the leg before pricing.
        Shipment shipment = load.getShipment();
        if (shipment != null) {
            shipment.setDistanceKm(request.getDistanceKm());
            shipment.setWeightKg(request.getWeightKg());
            shipment.setVolumeM3(request.getVolumeM3());
            shipment.setContainerCount(request.getContainerCount());
            shipment.setPieceCount(request.getPieceCount());
        }

        // Price the leg now: carrier cost (rate-card basis or rate × hours) +
        // per-mode platform commission + shipper total, onto the Shipment.
        pricingService.priceLoad(load);
        return LoadResponse.from(load, 0);
    }

    @Override
    @Transactional
    public ItineraryResponse createIntermodalLoad(Shipper shipper, CreateIntermodalLoadRequest request) {
        if (request.getLegs() == null || request.getLegs().isEmpty()) {
            throw new IllegalArgumentException("An intermodal load needs at least one leg");
        }
        String currency = request.getCurrency() != null
                ? request.getCurrency()
                : (shipper.getCurrency() != null ? shipper.getCurrency() : "EUR");

        List<TmsTreeService.LegInput> legs = new ArrayList<>();
        int i = 1;
        for (var leg : request.getLegs()) {
            if (leg.getPickupLocation() == null || leg.getPickupLocation().isBlank()
                    || leg.getDeliveryLocation() == null || leg.getDeliveryLocation().isBlank()) {
                throw new IllegalArgumentException("Leg " + i + " needs a pickup and delivery location");
            }
            boolean hasQuantity = leg.getDistanceKm() != null || leg.getWeightKg() != null
                    || leg.getVolumeM3() != null || leg.getContainerCount() != null
                    || leg.getPieceCount() != null;
            if (leg.getRatePerHour() == null && !hasQuantity) {
                throw new IllegalArgumentException(
                        "Leg " + i + " needs a quantity for its mode (or a rate per hour)");
            }
            legs.add(new TmsTreeService.LegInput(
                    parseMode(leg.getTransportMode()),
                    leg.getPickupLocation(), leg.getDeliveryLocation(),
                    leg.getPickupCountry(), leg.getDeliveryCountry(),
                    leg.getRatePerHour(), leg.getEstimatedDurationHours(),
                    leg.getRequiredLicenceCategory(),
                    leg.getDistanceKm(), leg.getWeightKg(), leg.getVolumeM3(),
                    leg.getContainerCount(), leg.getPieceCount()));
            i++;
        }

        Itinerary itinerary = tmsTreeService.createIntermodalTreeFor(shipper,
                new TmsTreeService.IntermodalOrderInput(
                        request.getTitle(), request.getDescription(), request.getDateNeeded(),
                        currency, legs));
        return ItineraryResponse.from(itinerary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItineraryResponse> getItinerariesByShipper(Shipper shipper) {
        return itineraryRepository.findByShipperOrderByCreatedAtDesc(shipper).stream()
                .map(ItineraryResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ItineraryResponse getItineraryById(Long id, Shipper shipper) {
        Itinerary it = itineraryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found"));
        if (shipper != null && (it.getShipper() == null
                || !it.getShipper().getId().equals(shipper.getId()))) {
            throw new IllegalArgumentException("You can only view your own itineraries");
        }
        return ItineraryResponse.from(it);
    }

    /** Parse a client mode string to {@link Shipment.Mode}, defaulting to ROAD.
     *  INTERMODAL is rejected — a leg always resolves to one concrete mode;
     *  the itinerary's own INTERMODAL label is derived from its legs, never
     *  assigned to one (see {@link Itinerary#getMode()}). */
    private static Shipment.Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return Shipment.Mode.ROAD;
        Shipment.Mode mode;
        try {
            mode = Shipment.Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Shipment.Mode.ROAD;
        }
        if (mode == Shipment.Mode.INTERMODAL) {
            throw new IllegalArgumentException(
                    "Leg transport mode cannot be INTERMODAL — each leg must be one concrete mode");
        }
        return mode;
    }

    @Override
    public List<LoadResponse> getLoadsByShipper(Shipper shipper) {
        return loadRepository.findByShipperOrderByCreatedAtDesc(shipper).stream()
                .map(load -> LoadResponse.from(load, applicationRepository.findByLoad(load).size()))
                .collect(Collectors.toList());
    }

    @Override
    public LoadResponse getLoadById(Long id) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));
        return LoadResponse.from(load, applicationRepository.findByLoad(load).size());
    }

    @Override
    @Transactional(readOnly = true)
    public LoadResponse getLoadById(Long id, Shipper shipper) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));
        // Ownership: a shipper may read only their own loads; admin passes null.
        if (shipper != null && !load.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only view your own loads");
        }
        return LoadResponse.from(load, applicationRepository.findByLoad(load).size());
    }

    @Override
    @Transactional
    public LoadResponse updateLoad(Long loadId, Shipper shipper, CreateLoadRequest request) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));

        // Ownership: a shipper may edit only their own loads; admin passes null.
        if (shipper != null && !load.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only update your own loads");
        }
        // Only OPEN loads are editable — once a carrier is assigned the price and
        // route are committed, and COMPLETED/CANCELLED are terminal.
        if (load.getStatus() != LoadStatus.OPEN) {
            throw new IllegalArgumentException("Only open loads can be edited");
        }
        Shipment shipment = load.getShipment();
        if (shipment == null) {
            throw new IllegalStateException("Load has no shipment leg to update");
        }
        if (shipment.getItinerary() != null) {
            throw new IllegalArgumentException("Intermodal legs cannot be edited here");
        }

        Shipper owner = load.getShipper();

        // Load-level carrier fields.
        load.setEstimatedDurationHours(request.getEstimatedDurationHours());
        load.setRatePerHour(request.getRatePerHour());
        load.setRequiredLicenceCategory(request.getRequiredLicenceCategory());
        if (request.getCurrency() != null) load.setCurrency(request.getCurrency());

        // Re-shape the tree in place (order metadata, shipment mode/countries, route).
        tmsTreeService.updateTreeFor(load, TmsTreeService.TmsOrderInput.fromRequest(request, owner));

        // Re-attach the per-mode pricing quantities (null clears) then re-price —
        // identical order to createLoad.
        shipment.setDistanceKm(request.getDistanceKm());
        shipment.setWeightKg(request.getWeightKg());
        shipment.setVolumeM3(request.getVolumeM3());
        shipment.setContainerCount(request.getContainerCount());
        shipment.setPieceCount(request.getPieceCount());
        pricingService.priceLoad(load);

        load = loadRepository.save(load);
        return LoadResponse.from(load, applicationRepository.findByLoad(load).size());
    }

    @Override
    public List<LoadResponse> getMatchingLoads(Carrier carrier) {
        // Browse must agree with apply-time validation, which matches licences
        // through the LicenceCategory.covers() lattice (e.g. a C+E holder may
        // take a C load; UK HGV class 1 ≡ EU C+E) rather than exact category
        // equality. So fetch all OPEN loads and apply the same satisfies() check
        // LoadApplicationServiceImpl uses — otherwise a carrier sees only
        // exact-match loads and misses ones they're actually entitled to apply
        // for (and a null-licence carrier was previously shown loads they can't).
        List<Load> loads = loadRepository.findByStatusOrderByDateNeededAsc(LoadStatus.OPEN);
        String have = carrier.getLicenceCategory();

        // Lane filter: when the carrier has configured at least one (origin →
        // destination) country pair, restrict matches to loads on those lanes.
        // Carriers with no lanes see everything (existing behaviour).
        List<CarrierLane> lanes = carrierLaneService.findAllForCarrier(carrier);

        // Licence + lane are pure in-memory predicates; apply them first so the
        // two DB lookups below run only over the surviving candidate set.
        List<Load> candidates = loads.stream()
                .filter(load -> carrier.supportsMode(load.getMode()))
                .filter(load -> credentialMatchers.satisfies(load.getMode(), have,
                        carrier.getCredentials(), load.getRequiredLicenceCategory()))
                .filter(load -> matchesAnyLane(load, lanes))
                .collect(Collectors.toList());

        // Batch the per-load lookups: per-mode remaining hours (declared − committed)
        // for all candidate dates in a constant number of queries, application counts
        // for all candidates in one query — instead of N+1. Browse now agrees with the
        // per-mode apply-time gate: a load only shows if THAT mode's clock has room.
        Set<LocalDate> dates = candidates.stream()
                .map(Load::getDateNeeded).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Shipment.Mode, Map<LocalDate, Double>> remainingByModeDate =
                availabilityService.getRemainingHoursByModeAndDate(carrier, dates);
        Map<Long, Integer> applicationCounts = applicationCountsByLoadId(candidates);

        return candidates.stream()
                .filter(load -> {
                    if (load.getDateNeeded() == null) return false;
                    double remaining = remainingByModeDate
                            .getOrDefault(load.getMode(), Map.of())
                            .getOrDefault(load.getDateNeeded(), 0.0);
                    return remaining >= load.getEstimatedDurationHours();
                })
                .map(load -> LoadResponse.from(load, applicationCounts.getOrDefault(load.getId(), 0)))
                .collect(Collectors.toList());
    }

    /** Application counts keyed by load id, fetched in a single grouped query. */
    private Map<Long, Integer> applicationCountsByLoadId(List<Load> loads) {
        if (loads.isEmpty()) return Map.of();
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : applicationRepository.countByLoadIn(loads)) {
            counts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    private boolean matchesAnyLane(Load load, List<CarrierLane> lanes) {
        if (lanes.isEmpty()) return true;
        // CarrierLane is a road country-pair concept; non-road legs aren't
        // constrained by it (node-based lanes for sea/air arrive in M3).
        if (load.getMode() != Shipment.Mode.ROAD) return true;
        String origin = load.getPickupCountry();
        String destination = load.getDeliveryCountry();
        // Load without country metadata (no Shipment yet) doesn't match any lane.
        if (origin == null || destination == null) return false;
        for (CarrierLane lane : lanes) {
            if (Objects.equals(origin, lane.getOriginCountry())
                    && Objects.equals(destination, lane.getDestinationCountry())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public LoadResponse updateLoadStatus(Long loadId, Shipper shipper, LoadStatus status) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));

        if (!load.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only update your own loads");
        }

        validateStatusTransition(load.getStatus(), status);
        load.setStatus(status);
        load = loadRepository.save(load);

        // Log a cabotage row when work is marked complete and the trip
        // qualifies. No-op for loads that aren't cabotage (handled inside).
        if (status == LoadStatus.COMPLETED && load.getAssignedCarrier() != null) {
            cabotageService.recordIfApplicable(load.getAssignedCarrier(), load);
        }
        return LoadResponse.from(load, applicationRepository.findByLoad(load).size());
    }

    private static final Map<LoadStatus, Set<LoadStatus>> VALID_TRANSITIONS = Map.of(
            LoadStatus.OPEN, Set.of(LoadStatus.CANCELLED),
            LoadStatus.ASSIGNED, Set.of(LoadStatus.IN_PROGRESS, LoadStatus.CANCELLED),
            LoadStatus.IN_PROGRESS, Set.of(LoadStatus.COMPLETED),
            LoadStatus.COMPLETED, Set.of(),
            LoadStatus.CANCELLED, Set.of()
    );

    private void validateStatusTransition(LoadStatus current, LoadStatus target) {
        Set<LoadStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalArgumentException(
                    "Cannot change status from " + current + " to " + target);
        }
    }
}
