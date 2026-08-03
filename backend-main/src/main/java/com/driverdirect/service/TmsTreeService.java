package com.driverdirect.service;

import com.driverdirect.dto.CreateLoadRequest;
import com.driverdirect.dto.CreateLoadStopRequest;
import com.driverdirect.model.Customer;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.ShipmentLine;
import com.driverdirect.model.Stop;
import com.driverdirect.model.TransportOrder;
import com.driverdirect.repository.CustomerRepository;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.LoadRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipmentLineRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.StopRepository;
import com.driverdirect.repository.TransportOrderRepository;
import com.driverdirect.util.CountryCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composes the Phase-0 TMS entity tree (Customer / TransportOrder / Shipment
 * / Stops / Locations / ShipmentLine) around a Load. Used by both the seed
 * backfill and live createLoad flows so the structure stays identical.
 */
@Service
@RequiredArgsConstructor
public class TmsTreeService {

    private final CustomerRepository customerRepository;
    private final LocationRepository locationRepository;
    private final TransportOrderRepository transportOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final StopRepository stopRepository;
    private final LoadRepository loadRepository;
    private final ItineraryRepository itineraryRepository;
    private final PricingService pricingService;

    /**
     * Customer-facing data needed to compose the tree around a Load. Replaces
     * the columns that used to live on Load (title/description/dateNeeded/
     * pickup/delivery/countries). Currency is the order-side currency — Load
     * keeps its own currency for the carrier rate.
     */
    public record TmsOrderInput(
            String title,
            String description,
            java.time.LocalDate dateNeeded,
            // Optional flexible-window context from a routing search (see
            // README.md, "Proposed: multimodal routing engine"); null for
            // every order created the normal way today. dateNeeded above
            // stays the authoritative single date regardless.
            java.time.LocalDate earliestReadyDate,
            java.time.LocalDate latestHandoverDate,
            java.time.LocalDate arrivalDeadline,
            String pickupLocation,
            String deliveryLocation,
            String pickupCountry,
            String deliveryCountry,
            String currency,
            // Full ordered route. When non-empty, used in preference to the
            // pickup/delivery pair above. Origin/destination countries on
            // Shipment are then taken from first PICKUP / last DELIVERY.
            List<TmsStopInput> stops,
            // Transport mode of the leg (M1). Null is treated as ROAD.
            Shipment.Mode mode) {

        // Legacy 8-arg constructor — seed data still calls it positionally.
        // Defaults to a single-leg ROAD move with no explicit route.
        public TmsOrderInput(String title, String description,
                             java.time.LocalDate dateNeeded,
                             String pickupLocation, String deliveryLocation,
                             String pickupCountry, String deliveryCountry,
                             String currency) {
            this(title, description, dateNeeded, null, null, null, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, Collections.emptyList(),
                    Shipment.Mode.ROAD);
        }

        // 9-arg route form (no explicit mode) — kept so existing multi-stop
        // seed / back-compat callers compile unchanged; mode defaults to ROAD.
        public TmsOrderInput(String title, String description,
                             java.time.LocalDate dateNeeded,
                             String pickupLocation, String deliveryLocation,
                             String pickupCountry, String deliveryCountry,
                             String currency, List<TmsStopInput> stops) {
            this(title, description, dateNeeded, null, null, null, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, stops, Shipment.Mode.ROAD);
        }

        // 9-arg mode form (no explicit route) — lets the seed compose a
        // single-leg non-road demo load. Differs from the route form by the
        // final parameter type, so there is no overload ambiguity.
        public TmsOrderInput(String title, String description,
                             java.time.LocalDate dateNeeded,
                             String pickupLocation, String deliveryLocation,
                             String pickupCountry, String deliveryCountry,
                             String currency, Shipment.Mode mode) {
            this(title, description, dateNeeded, null, null, null, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, Collections.emptyList(), mode);
        }

        public static TmsOrderInput fromRequest(CreateLoadRequest req, Shipper shipper) {
            String c = req.getCurrency() != null ? req.getCurrency() : shipper.getCurrency();
            List<TmsStopInput> stops = TmsStopInput.listFromRequest(req.getStops(), shipper.getCountry());

            // When the client sent a stops list, derive origin/destination
            // country from it; otherwise fall back to the explicit / shipper
            // defaults so legacy single-pickup/single-delivery callers behave
            // exactly as before.
            String pc;
            String dc;
            if (!stops.isEmpty()) {
                // A route must anchor on a real pickup and delivery. Without them
                // origin/destination silently fall back to the shipper's country
                // (see firstCountryOfType/lastCountryOfType), which mis-classifies
                // cabotage and breaks lane matching. Reject instead — this also
                // surfaces a mistyped stop type that listFromRequest coerced to
                // WAYPOINT.
                boolean hasPickup = stops.stream().anyMatch(s -> s.type() == Stop.StopType.PICKUP);
                boolean hasDelivery = stops.stream().anyMatch(s -> s.type() == Stop.StopType.DELIVERY);
                if (!hasPickup || !hasDelivery) {
                    throw new IllegalArgumentException(
                            "Route must include at least one PICKUP and one DELIVERY stop");
                }
                pc = firstCountryOfType(stops, Stop.StopType.PICKUP, shipper.getCountry());
                dc = lastCountryOfType(stops, Stop.StopType.DELIVERY, shipper.getCountry());
            } else {
                pc = req.getPickupCountry() != null ? req.getPickupCountry() : shipper.getCountry();
                dc = req.getDeliveryCountry() != null ? req.getDeliveryCountry() : shipper.getCountry();
            }

            return new TmsOrderInput(
                    req.getTitle(), req.getDescription(), req.getDateNeeded(),
                    req.getEarliestReadyDate(), req.getLatestHandoverDate(), req.getArrivalDeadline(),
                    req.getPickupLocation(), req.getDeliveryLocation(),
                    pc, dc, c, stops, parseMode(req.getTransportMode()));
        }

        /** Parse a client-supplied mode string to {@link Shipment.Mode},
         *  defaulting to ROAD on null/blank/unrecognised values. INTERMODAL is
         *  rejected outright: it's a derived, multi-leg-only label (see
         *  {@link Itinerary#getMode()}), never a concrete single leg's mode —
         *  allowing it here would let a standalone load carry a mode with no
         *  rate card, no marketed pricing, and no carriers opted in to match it. */
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
                        "INTERMODAL is not a valid mode for a single-leg load — post a multi-leg itinerary instead");
            }
            return mode;
        }

        private static String firstCountryOfType(List<TmsStopInput> ss, Stop.StopType t, String dflt) {
            return ss.stream().filter(s -> s.type() == t).map(TmsStopInput::country)
                    .filter(c -> c != null && !c.isBlank()).findFirst().orElse(dflt);
        }

        private static String lastCountryOfType(List<TmsStopInput> ss, Stop.StopType t, String dflt) {
            String found = dflt;
            for (TmsStopInput s : ss) {
                if (s.type() == t && s.country() != null && !s.country().isBlank()) {
                    found = s.country();
                }
            }
            return found;
        }
    }

    /** One ordered stop on the route. {@link #locationName} is mandatory; the
     *  rest are best-effort enrichment for newly-upserted Locations. */
    public record TmsStopInput(
            Stop.StopType type,
            String locationName,
            String addressLine,
            String city,
            String country,
            Double latitude,
            Double longitude,
            LocalDateTime earliestAt,
            LocalDateTime latestAt) {

        public static List<TmsStopInput> listFromRequest(List<CreateLoadStopRequest> raw, String defaultCountry) {
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<TmsStopInput> out = new ArrayList<>(raw.size());
            for (CreateLoadStopRequest r : raw) {
                if (r.getLocationName() == null || r.getLocationName().isBlank()) continue;
                Stop.StopType type;
                try {
                    type = Stop.StopType.valueOf(r.getType());
                } catch (Exception e) {
                    type = Stop.StopType.WAYPOINT;
                }
                String country = (r.getCountry() != null && !r.getCountry().isBlank())
                        ? r.getCountry() : defaultCountry;
                out.add(new TmsStopInput(
                        type, r.getLocationName(), r.getAddressLine(), r.getCity(),
                        country, r.getLatitude(), r.getLongitude(),
                        r.getEarliestAt(), r.getLatestAt()));
            }
            return out;
        }
    }

    /**
     * Build the full TMS tree for an existing Load and attach the resulting
     * Shipment back to the Load (caller re-saves the Load).
     */
    @Transactional
    public Shipment createTreeFor(Load load, TmsOrderInput input) {
        Shipper shipper = load.getShipper();
        Customer customer = customerRepository.findFirstByShipperOrderByIdAsc(shipper)
                .orElseGet(() -> customerRepository.save(
                        new Customer(shipper, shipper.getCompanyName() + " (default)")));

        TransportOrder order = new TransportOrder();
        order.setShipper(shipper);
        order.setCustomer(customer);
        order.setTitle(input.title());
        order.setDescription(input.description());
        order.setDateNeeded(input.dateNeeded());
        order.setEarliestReadyDate(input.earliestReadyDate());
        order.setLatestHandoverDate(input.latestHandoverDate());
        order.setArrivalDeadline(input.arrivalDeadline());
        order.setCurrency(input.currency());
        order.setStatus(mapOrderStatus(load.getStatus()));
        order = transportOrderRepository.save(order);

        Shipment shipment = new Shipment();
        shipment.setShipper(shipper);
        shipment.setMode(input.mode() != null ? input.mode() : Shipment.Mode.ROAD);
        shipment.setStatus(mapShipmentStatus(load.getStatus()));
        shipment.setCurrency(input.currency());
        // Canonicalise to uppercase so the lane filter (Objects.equals) and the
        // cabotage origin==destination test compare against the same form lanes
        // and cabotage rows are stored in.
        shipment.setOriginCountry(CountryCodes.normalize(input.pickupCountry()));
        shipment.setDestinationCountry(CountryCodes.normalize(input.deliveryCountry()));
        shipment = shipmentRepository.save(shipment);

        if (input.stops() != null && !input.stops().isEmpty()) {
            persistStopList(shipment, input.stops(), input.dateNeeded());
        } else {
            persistLegacyPickupDelivery(shipment, input);
        }

        ShipmentLine line = new ShipmentLine();
        line.setShipment(shipment);
        line.setOrder(order);
        shipmentLineRepository.save(line);

        load.setShipment(shipment);
        return shipment;
    }

    /**
     * Re-shape the TMS tree of an existing single-leg Load in place: refresh the
     * order metadata, the shipment's mode/currency/origin-destination countries,
     * and replace the route. Mirrors {@link #createTreeFor} but mutates existing
     * entities. Per-mode quantities + (re)pricing stay with the caller, exactly
     * as on create.
     */
    @Transactional
    public void updateTreeFor(Load load, TmsOrderInput input) {
        Shipment shipment = load.getShipment();
        if (shipment == null) {
            throw new IllegalStateException("Load has no shipment leg to update");
        }
        if (!shipment.getShipmentLines().isEmpty()) {
            TransportOrder order = shipment.getShipmentLines().get(0).getOrder();
            order.setTitle(input.title());
            order.setDescription(input.description());
            order.setDateNeeded(input.dateNeeded());
            order.setEarliestReadyDate(input.earliestReadyDate());
            order.setLatestHandoverDate(input.latestHandoverDate());
            order.setArrivalDeadline(input.arrivalDeadline());
            order.setCurrency(input.currency());
            transportOrderRepository.save(order);
        }

        shipment.setMode(input.mode() != null ? input.mode() : Shipment.Mode.ROAD);
        shipment.setCurrency(input.currency());
        shipment.setOriginCountry(CountryCodes.normalize(input.pickupCountry()));
        shipment.setDestinationCountry(CountryCodes.normalize(input.deliveryCountry()));

        // Stops have no cascade / orphanRemoval and a unique (shipment, sequence)
        // constraint, so delete the old rows in a single batch (flushed at once)
        // before re-inserting — otherwise reusing sequences 1..n would collide.
        List<Stop> existing = stopRepository.findByShipmentOrderBySequenceAsc(shipment);
        if (!existing.isEmpty()) stopRepository.deleteAllInBatch(existing);
        shipment.getStops().clear();
        if (input.stops() != null && !input.stops().isEmpty()) {
            persistStopList(shipment, input.stops(), input.dateNeeded());
        } else {
            persistLegacyPickupDelivery(shipment, input);
        }
        shipmentRepository.save(shipment);
    }

    /** Order-level metadata + the ordered legs of an intermodal movement (M2). */
    public record IntermodalOrderInput(
            String title, String description, LocalDate dateNeeded,
            // Optional flexible-window context from a routing search — see the
            // matching fields on TmsOrderInput. Null for every itinerary
            // created the normal way (shipper-authored, explicit legs) today.
            LocalDate earliestReadyDate, LocalDate latestHandoverDate, LocalDate arrivalDeadline,
            String currency, List<LegInput> legs) {

        // Legacy 5-arg constructor — every existing caller (createIntermodalLoad,
        // updateIntermodalLoad, seed data) predates the flexible-window fields.
        public IntermodalOrderInput(String title, String description, LocalDate dateNeeded,
                                    String currency, List<LegInput> legs) {
            this(title, description, dateNeeded, null, null, null, currency, legs);
        }
    }

    /** One leg of an intermodal movement: its mode, route endpoints, the carrier
     *  rate, and optional per-mode pricing quantities (priced independently,
     *  then rolled up).
     *
     *  <p>{@code pickupLocationId}/{@code deliveryLocationId} pin the endpoints
     *  to existing Location rows and win over the names when set — see
     *  {@link #resolveLegLocation}. Null for every caller that identifies its
     *  endpoints by name (the seed, the post-a-load form). */
    public record LegInput(
            Shipment.Mode mode,
            String pickupLocation, String deliveryLocation,
            String pickupCountry, String deliveryCountry,
            BigDecimal ratePerHour, Double estimatedDurationHours,
            String requiredLicenceCategory,
            BigDecimal distanceKm, BigDecimal weightKg, BigDecimal volumeM3,
            Integer containerCount, Integer pieceCount,
            Long pickupLocationId, Long deliveryLocationId) {

        /** Back-compat constructor for name-addressed legs (no endpoint ids). */
        public LegInput(Shipment.Mode mode, String pickupLocation, String deliveryLocation,
                        String pickupCountry, String deliveryCountry, BigDecimal ratePerHour,
                        Double estimatedDurationHours, String requiredLicenceCategory,
                        BigDecimal distanceKm, BigDecimal weightKg, BigDecimal volumeM3,
                        Integer containerCount, Integer pieceCount) {
            this(mode, pickupLocation, deliveryLocation, pickupCountry, deliveryCountry,
                    ratePerHour, estimatedDurationHours, requiredLicenceCategory,
                    distanceKm, weightKg, volumeM3, containerCount, pieceCount, null, null);
        }

        /** Back-compat constructor for legs priced by rate × hours (no quantities). */
        public LegInput(Shipment.Mode mode, String pickupLocation, String deliveryLocation,
                        String pickupCountry, String deliveryCountry, BigDecimal ratePerHour,
                        Double estimatedDurationHours, String requiredLicenceCategory) {
            this(mode, pickupLocation, deliveryLocation, pickupCountry, deliveryCountry,
                    ratePerHour, estimatedDurationHours, requiredLicenceCategory,
                    null, null, null, null, null);
        }
    }

    /**
     * Build a true intermodal movement: one TransportOrder and one Itinerary
     * sequencing N single-mode leg-Shipments, each with its own carrier Load
     * (Load), stops, and price. Each leg is priced via {@link PricingService},
     * then the itinerary totals are rolled up. Returns the saved Itinerary.
     *
     * <p>The single-leg {@link #createTreeFor} path is untouched — a standalone
     * load still produces an itinerary-less Shipment exactly as before.
     */
    @Transactional
    public Itinerary createIntermodalTreeFor(Shipper shipper, IntermodalOrderInput input) {
        Customer customer = customerRepository.findFirstByShipperOrderByIdAsc(shipper)
                .orElseGet(() -> customerRepository.save(
                        new Customer(shipper, shipper.getCompanyName() + " (default)")));
        String currency = input.currency() != null ? input.currency()
                : (shipper.getCurrency() != null ? shipper.getCurrency() : "EUR");

        TransportOrder order = new TransportOrder();
        order.setShipper(shipper);
        order.setCustomer(customer);
        order.setTitle(input.title());
        order.setDescription(input.description());
        order.setDateNeeded(input.dateNeeded());
        order.setEarliestReadyDate(input.earliestReadyDate());
        order.setLatestHandoverDate(input.latestHandoverDate());
        order.setArrivalDeadline(input.arrivalDeadline());
        order.setCurrency(currency);
        order.setStatus(TransportOrder.OrderStatus.NEW);
        order = transportOrderRepository.save(order);

        Itinerary itinerary = new Itinerary();
        itinerary.setShipper(shipper);
        itinerary.setOrder(order);
        itinerary.setCurrency(currency);
        itinerary.setStatus(Itinerary.ItineraryStatus.PLANNED);
        itinerary = itineraryRepository.save(itinerary);

        int seq = 1;
        for (LegInput leg : input.legs()) {
            // Resolve the endpoints before building the leg: an id carried
            // through from an accepted route pins the exact Location the
            // planner used, and that row's country then stands in below when
            // the caller didn't send one.
            Location pickupLoc = resolveLegLocation(
                    leg.pickupLocationId(), leg.pickupLocation(), leg.pickupCountry(), shipper);
            Location deliveryLoc = resolveLegLocation(
                    leg.deliveryLocationId(), leg.deliveryLocation(), leg.deliveryCountry(), shipper);

            // Carrier assignment (Load) for this leg.
            Load load = new Load();
            load.setShipper(shipper);
            // Rate × hours is the fallback basis; quantity-priced legs may omit
            // them, so default to zero (Load requires non-null values).
            load.setEstimatedDurationHours(leg.estimatedDurationHours() != null ? leg.estimatedDurationHours() : 0.0);
            load.setRatePerHour(leg.ratePerHour() != null ? leg.ratePerHour() : BigDecimal.ZERO);
            load.setRequiredLicenceCategory(leg.requiredLicenceCategory());
            load.setCurrency(currency);
            load.setStatus(LoadStatus.OPEN);
            load = loadRepository.save(load);

            // The physical leg, attached to the itinerary at its sequence.
            Shipment shipment = new Shipment();
            shipment.setShipper(shipper);
            shipment.setMode(leg.mode() != null ? leg.mode() : Shipment.Mode.ROAD);
            shipment.setStatus(Shipment.ShipmentStatus.PLANNED);
            shipment.setCurrency(currency);
            shipment.setItinerary(itinerary);
            shipment.setLegSequence(seq);
            shipment.setOriginCountry(countryOf(leg.pickupCountry(), pickupLoc));
            shipment.setDestinationCountry(countryOf(leg.deliveryCountry(), deliveryLoc));
            shipment.setDistanceKm(leg.distanceKm());
            shipment.setWeightKg(leg.weightKg());
            shipment.setVolumeM3(leg.volumeM3());
            shipment.setContainerCount(leg.containerCount());
            shipment.setPieceCount(leg.pieceCount());
            shipment = shipmentRepository.save(shipment);

            persistPickupDelivery(shipment, pickupLoc, deliveryLoc, input.dateNeeded());

            ShipmentLine line = new ShipmentLine();
            line.setShipment(shipment);
            line.setOrder(order);
            shipmentLineRepository.save(line);

            load.setShipment(shipment);
            loadRepository.save(load);

            // Price this leg now (carrier cost + per-mode commission on the leg).
            pricingService.priceLoad(load);

            // Keep the inverse side in sync so the just-built Itinerary reports
            // its legs + derived mode correctly when mapped in this same session
            // (the @OneToMany is mappedBy/inverse and isn't auto-populated).
            itinerary.getLegs().add(shipment);
            seq++;
        }

        // Roll the priced legs up into the itinerary totals.
        pricingService.recalcItinerary(itinerary);
        return itinerary;
    }

    /**
     * Reshape an existing Itinerary in place: order metadata, currency, and
     * each leg's fields (mode/countries/quantities/route/rate) — matched to
     * the existing legs by position, which lines up with {@code legSequence}
     * order thanks to {@code @OrderBy} on {@link Itinerary#getLegs()}.
     *
     * <p>The leg count can't change here — legs have no cascade/orphanRemoval
     * (same as Stops, see {@link #updateTreeFor}), so adding/removing one
     * would mean deleting a whole Load/Shipment/Stop chain; out of scope for
     * now, so that's rejected below (cancel and repost instead).
     */
    @Transactional
    public void updateIntermodalTreeFor(Itinerary itinerary, IntermodalOrderInput input) {
        List<Shipment> legs = itinerary.getLegs();
        if (legs.size() != input.legs().size()) {
            throw new IllegalArgumentException(
                    "Cannot add or remove legs when editing an itinerary — cancel and repost instead");
        }

        TransportOrder order = itinerary.getOrder();
        if (order != null) {
            order.setTitle(input.title());
            order.setDescription(input.description());
            order.setDateNeeded(input.dateNeeded());
            order.setEarliestReadyDate(input.earliestReadyDate());
            order.setLatestHandoverDate(input.latestHandoverDate());
            order.setArrivalDeadline(input.arrivalDeadline());
            order.setCurrency(input.currency());
            transportOrderRepository.save(order);
        }
        itinerary.setCurrency(input.currency());
        itineraryRepository.save(itinerary);

        for (int i = 0; i < legs.size(); i++) {
            Shipment leg = legs.get(i);
            LegInput li = input.legs().get(i);

            // Same id-wins endpoint resolution as the create path, scoped to
            // the itinerary's own shipper.
            Location pickupLoc = resolveLegLocation(li.pickupLocationId(), li.pickupLocation(),
                    li.pickupCountry(), itinerary.getShipper());
            Location deliveryLoc = resolveLegLocation(li.deliveryLocationId(), li.deliveryLocation(),
                    li.deliveryCountry(), itinerary.getShipper());

            leg.setMode(li.mode() != null ? li.mode() : Shipment.Mode.ROAD);
            leg.setOriginCountry(countryOf(li.pickupCountry(), pickupLoc));
            leg.setDestinationCountry(countryOf(li.deliveryCountry(), deliveryLoc));
            leg.setDistanceKm(li.distanceKm());
            leg.setWeightKg(li.weightKg());
            leg.setVolumeM3(li.volumeM3());
            leg.setContainerCount(li.containerCount());
            leg.setPieceCount(li.pieceCount());

            // Stops have no cascade/orphanRemoval — delete the old pair before
            // re-inserting, exactly like updateTreeFor does for a single leg.
            List<Stop> existingStops = stopRepository.findByShipmentOrderBySequenceAsc(leg);
            if (!existingStops.isEmpty()) stopRepository.deleteAllInBatch(existingStops);
            leg.getStops().clear();
            persistPickupDelivery(leg, pickupLoc, deliveryLoc, input.dateNeeded());

            leg = shipmentRepository.save(leg);

            Load load = loadRepository.findByShipment(leg)
                    .orElseThrow(() -> new IllegalStateException("Leg has no Load to update"));
            load.setEstimatedDurationHours(li.estimatedDurationHours() != null ? li.estimatedDurationHours() : 0.0);
            load.setRatePerHour(li.ratePerHour() != null ? li.ratePerHour() : BigDecimal.ZERO);
            load.setRequiredLicenceCategory(li.requiredLicenceCategory());
            loadRepository.save(load);

            // Re-price this leg now that its quantities/rate may have changed.
            pricingService.priceLoad(load);
        }

        // Roll the re-priced legs back up into the itinerary totals.
        pricingService.recalcItinerary(itinerary);
    }

    /** Multi-stop path: one Stop per TmsStopInput, 1-indexed in submission order. */
    private void persistStopList(Shipment shipment, List<TmsStopInput> stops,
                                 java.time.LocalDate dateNeeded) {
        int seq = 1;
        for (TmsStopInput s : stops) {
            Location loc = upsertLocation(s);
            if (loc == null) continue;
            Stop stop = new Stop();
            stop.setShipment(shipment);
            stop.setSequence(seq++);
            stop.setType(s.type());
            stop.setLocation(loc);
            // Windows: prefer the client-supplied ones, otherwise fall back to a
            // morning/afternoon default keyed on the order's dateNeeded.
            if (s.earliestAt() != null || s.latestAt() != null) {
                stop.setEarliestAt(s.earliestAt());
                stop.setLatestAt(s.latestAt());
            } else if (dateNeeded != null) {
                if (s.type() == Stop.StopType.PICKUP) {
                    stop.setEarliestAt(dateNeeded.atTime(8, 0));
                    stop.setLatestAt(dateNeeded.atTime(11, 0));
                } else if (s.type() == Stop.StopType.DELIVERY) {
                    stop.setEarliestAt(dateNeeded.atTime(13, 0));
                    stop.setLatestAt(dateNeeded.atTime(18, 0));
                }
            }
            stopRepository.save(stop);
            // Sync the inverse side so the just-built/updated tree reports its
            // stops in this session (the @OneToMany is mappedBy/inverse).
            shipment.getStops().add(stop);
        }
    }

    /** Legacy single-pickup / single-delivery path used by seed data and any
     *  client that hasn't migrated to the stops list yet. */
    private void persistLegacyPickupDelivery(Shipment shipment, TmsOrderInput input) {
        persistPickupDelivery(shipment, input.pickupLocation(), input.pickupCountry(),
                input.deliveryLocation(), input.deliveryCountry(), input.dateNeeded());
    }

    /** Name-addressed pair: upsert both endpoints, then delegate. Used by the
     *  legacy single-leg path, which has no endpoint ids to carry. */
    private void persistPickupDelivery(Shipment shipment, String pickupName, String pickupCountry,
                                       String deliveryName, String deliveryCountry, LocalDate dateNeeded) {
        persistPickupDelivery(shipment, upsertLocation(pickupName, pickupCountry),
                upsertLocation(deliveryName, deliveryCountry), dateNeeded);
    }

    /** A single PICKUP + DELIVERY pair for one shipment leg, against endpoints
     *  the caller has already resolved (by id, or upserted from a name above). */
    private void persistPickupDelivery(Shipment shipment, Location pickupLoc, Location deliveryLoc,
                                       LocalDate dateNeeded) {
        if (pickupLoc != null) {
            Stop pickup = new Stop();
            pickup.setShipment(shipment);
            pickup.setSequence(1);
            pickup.setType(Stop.StopType.PICKUP);
            pickup.setLocation(pickupLoc);
            if (dateNeeded != null) {
                pickup.setEarliestAt(dateNeeded.atTime(8, 0));
                pickup.setLatestAt(dateNeeded.atTime(11, 0));
            }
            stopRepository.save(pickup);
            // Sync the inverse side so the just-built tree reports its stops in
            // the same session (the @OneToMany is mappedBy and isn't auto-filled).
            shipment.getStops().add(pickup);
        }

        if (deliveryLoc != null) {
            Stop delivery = new Stop();
            delivery.setShipment(shipment);
            delivery.setSequence(2);
            delivery.setType(Stop.StopType.DELIVERY);
            delivery.setLocation(deliveryLoc);
            if (dateNeeded != null) {
                delivery.setEarliestAt(dateNeeded.atTime(13, 0));
                delivery.setLatestAt(dateNeeded.atTime(18, 0));
            }
            stopRepository.save(delivery);
            shipment.getStops().add(delivery);
        }
    }

    /**
     * Resolve one leg endpoint to a Location. A supplied {@code locationId}
     * wins — that is the point of carrying it: the Stop binds to the exact row
     * the caller meant (typed terminal, coordinates, timezone, UN/LOCODE
     * intact) instead of being re-derived by name. The name+country upsert
     * below is a lossy round-trip: it matches on name+country only, so a
     * near-miss silently creates a second, untyped, coordinate-less ADDRESS
     * that the routing graph then treats as a different node.
     *
     * <p>Ids are tenant-checked here as well as at the routing entry point —
     * this method is reachable from the public itinerary POST, not only from
     * an accepted route — and an inaccessible id fails exactly like an unknown
     * one, revealing nothing. Falling back to the name path keeps every
     * pre-routing caller behaving unchanged.
     */
    private Location resolveLegLocation(Long locationId, String name, String country, Shipper owner) {
        if (locationId == null) return upsertLocation(name, country);
        return locationRepository.findById(locationId)
                .filter(loc -> loc.isAccessibleBy(owner))
                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + locationId));
    }

    /** The country to stamp on a leg: the caller's when given, otherwise the
     *  resolved Location's — so an endpoint identified by id alone needn't
     *  echo a country the row already knows. */
    private String countryOf(String explicit, Location resolved) {
        String normalized = CountryCodes.normalize(explicit);
        if (normalized != null) return normalized;
        return resolved != null ? resolved.getCountry() : null;
    }

    private Location upsertLocation(String name, String country) {
        if (name == null || name.isBlank()) return null;
        String iso = CountryCodes.normalize(country) == null ? "IE" : CountryCodes.normalize(country);
        return locationRepository.findFirstByNameIgnoreCaseAndCountry(name, iso).orElseGet(() -> {
            Location loc = new Location();
            loc.setName(name);
            loc.setAddressLine(name);
            loc.setCountry(iso);
            return locationRepository.save(loc);
        });
    }

    /** Richer upsert that captures address line, city, and lat/lng from the
     *  client when a fresh Location is being created. Existing rows are not
     *  back-filled so multiple loads at the same name+country stay stable. */
    private Location upsertLocation(TmsStopInput s) {
        if (s.locationName() == null || s.locationName().isBlank()) return null;
        String iso = CountryCodes.normalize(s.country()) == null ? "IE" : CountryCodes.normalize(s.country());
        return locationRepository.findFirstByNameIgnoreCaseAndCountry(s.locationName(), iso)
                .orElseGet(() -> {
                    Location loc = new Location();
                    loc.setName(s.locationName());
                    loc.setAddressLine(s.addressLine() != null ? s.addressLine() : s.locationName());
                    loc.setCity(s.city());
                    loc.setCountry(iso);
                    loc.setLatitude(s.latitude());
                    loc.setLongitude(s.longitude());
                    return locationRepository.save(loc);
                });
    }

    private TransportOrder.OrderStatus mapOrderStatus(LoadStatus js) {
        switch (js) {
            case OPEN:        return TransportOrder.OrderStatus.NEW;
            case ASSIGNED:    return TransportOrder.OrderStatus.PLANNED;
            case IN_PROGRESS: return TransportOrder.OrderStatus.IN_EXECUTION;
            case COMPLETED:   return TransportOrder.OrderStatus.COMPLETED;
            case CANCELLED:   return TransportOrder.OrderStatus.CANCELLED;
            default:          return TransportOrder.OrderStatus.NEW;
        }
    }

    private Shipment.ShipmentStatus mapShipmentStatus(LoadStatus js) {
        switch (js) {
            case OPEN:        return Shipment.ShipmentStatus.PLANNED;
            case ASSIGNED:    return Shipment.ShipmentStatus.ACCEPTED;
            case IN_PROGRESS: return Shipment.ShipmentStatus.IN_TRANSIT;
            case COMPLETED:   return Shipment.ShipmentStatus.DELIVERED;
            case CANCELLED:   return Shipment.ShipmentStatus.CANCELLED;
            default:          return Shipment.ShipmentStatus.PLANNED;
        }
    }
}
