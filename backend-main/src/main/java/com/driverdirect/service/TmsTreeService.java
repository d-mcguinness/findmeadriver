package com.driverdirect.service;

import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.CreateJobStopRequest;
import com.driverdirect.model.Customer;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.ShipmentLine;
import com.driverdirect.model.Stop;
import com.driverdirect.model.TransportOrder;
import com.driverdirect.repository.CustomerRepository;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.JobRepository;
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
 * / Stops / Locations / ShipmentLine) around a Job. Used by both the seed
 * backfill and live createJob flows so the structure stays identical.
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
    private final JobRepository jobRepository;
    private final ItineraryRepository itineraryRepository;
    private final PricingService pricingService;

    /**
     * Customer-facing data needed to compose the tree around a Job. Replaces
     * the columns that used to live on Job (title/description/dateNeeded/
     * pickup/delivery/countries). Currency is the order-side currency — Job
     * keeps its own currency for the carrier rate.
     */
    public record TmsOrderInput(
            String title,
            String description,
            java.time.LocalDate dateNeeded,
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
            this(title, description, dateNeeded, pickupLocation, deliveryLocation,
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
            this(title, description, dateNeeded, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, stops, Shipment.Mode.ROAD);
        }

        // 9-arg mode form (no explicit route) — lets the seed compose a
        // single-leg non-road demo job. Differs from the route form by the
        // final parameter type, so there is no overload ambiguity.
        public TmsOrderInput(String title, String description,
                             java.time.LocalDate dateNeeded,
                             String pickupLocation, String deliveryLocation,
                             String pickupCountry, String deliveryCountry,
                             String currency, Shipment.Mode mode) {
            this(title, description, dateNeeded, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, Collections.emptyList(), mode);
        }

        public static TmsOrderInput fromRequest(CreateJobRequest req, Employer employer) {
            String c = req.getCurrency() != null ? req.getCurrency() : employer.getCurrency();
            List<TmsStopInput> stops = TmsStopInput.listFromRequest(req.getStops(), employer.getCountry());

            // When the client sent a stops list, derive origin/destination
            // country from it; otherwise fall back to the explicit / employer
            // defaults so legacy single-pickup/single-delivery callers behave
            // exactly as before.
            String pc;
            String dc;
            if (!stops.isEmpty()) {
                // A route must anchor on a real pickup and delivery. Without them
                // origin/destination silently fall back to the employer's country
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
                pc = firstCountryOfType(stops, Stop.StopType.PICKUP, employer.getCountry());
                dc = lastCountryOfType(stops, Stop.StopType.DELIVERY, employer.getCountry());
            } else {
                pc = req.getPickupCountry() != null ? req.getPickupCountry() : employer.getCountry();
                dc = req.getDeliveryCountry() != null ? req.getDeliveryCountry() : employer.getCountry();
            }

            return new TmsOrderInput(
                    req.getTitle(), req.getDescription(), req.getDateNeeded(),
                    req.getPickupLocation(), req.getDeliveryLocation(),
                    pc, dc, c, stops, parseMode(req.getTransportMode()));
        }

        /** Parse a client-supplied mode string to {@link Shipment.Mode},
         *  defaulting to ROAD on null/blank/unrecognised values. */
        private static Shipment.Mode parseMode(String raw) {
            if (raw == null || raw.isBlank()) return Shipment.Mode.ROAD;
            try {
                return Shipment.Mode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Shipment.Mode.ROAD;
            }
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

        public static List<TmsStopInput> listFromRequest(List<CreateJobStopRequest> raw, String defaultCountry) {
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<TmsStopInput> out = new ArrayList<>(raw.size());
            for (CreateJobStopRequest r : raw) {
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
     * Build the full TMS tree for an existing Job and attach the resulting
     * Shipment back to the Job (caller re-saves the Job).
     */
    @Transactional
    public Shipment createTreeFor(Job job, TmsOrderInput input) {
        Employer employer = job.getEmployer();
        Customer customer = customerRepository.findFirstByEmployerOrderByIdAsc(employer)
                .orElseGet(() -> customerRepository.save(
                        new Customer(employer, employer.getCompanyName() + " (default)")));

        TransportOrder order = new TransportOrder();
        order.setEmployer(employer);
        order.setCustomer(customer);
        order.setTitle(input.title());
        order.setDescription(input.description());
        order.setDateNeeded(input.dateNeeded());
        order.setCurrency(input.currency());
        order.setStatus(mapOrderStatus(job.getStatus()));
        order = transportOrderRepository.save(order);

        Shipment shipment = new Shipment();
        shipment.setEmployer(employer);
        shipment.setMode(input.mode() != null ? input.mode() : Shipment.Mode.ROAD);
        shipment.setStatus(mapShipmentStatus(job.getStatus()));
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

        job.setShipment(shipment);
        return shipment;
    }

    /** Order-level metadata + the ordered legs of an intermodal movement (M2). */
    public record IntermodalOrderInput(
            String title, String description, LocalDate dateNeeded, String currency,
            List<LegInput> legs) {}

    /** One leg of an intermodal movement: its mode, route endpoints, the carrier
     *  rate, and optional per-mode pricing quantities (priced independently,
     *  then rolled up). */
    public record LegInput(
            Shipment.Mode mode,
            String pickupLocation, String deliveryLocation,
            String pickupCountry, String deliveryCountry,
            BigDecimal ratePerHour, Double estimatedDurationHours,
            String requiredLicenceCategory,
            BigDecimal distanceKm, BigDecimal weightKg, BigDecimal volumeM3,
            Integer containerCount, Integer pieceCount) {

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
     * sequencing N single-mode leg-Shipments, each with its own carrier Job
     * (Load), stops, and price. Each leg is priced via {@link PricingService},
     * then the itinerary totals are rolled up. Returns the saved Itinerary.
     *
     * <p>The single-leg {@link #createTreeFor} path is untouched — a standalone
     * job still produces an itinerary-less Shipment exactly as before.
     */
    @Transactional
    public Itinerary createIntermodalTreeFor(Employer employer, IntermodalOrderInput input) {
        Customer customer = customerRepository.findFirstByEmployerOrderByIdAsc(employer)
                .orElseGet(() -> customerRepository.save(
                        new Customer(employer, employer.getCompanyName() + " (default)")));
        String currency = input.currency() != null ? input.currency()
                : (employer.getCurrency() != null ? employer.getCurrency() : "EUR");

        TransportOrder order = new TransportOrder();
        order.setEmployer(employer);
        order.setCustomer(customer);
        order.setTitle(input.title());
        order.setDescription(input.description());
        order.setDateNeeded(input.dateNeeded());
        order.setCurrency(currency);
        order.setStatus(TransportOrder.OrderStatus.NEW);
        order = transportOrderRepository.save(order);

        Itinerary itinerary = new Itinerary();
        itinerary.setEmployer(employer);
        itinerary.setOrder(order);
        itinerary.setCurrency(currency);
        itinerary.setStatus(Itinerary.ItineraryStatus.PLANNED);
        itinerary = itineraryRepository.save(itinerary);

        int seq = 1;
        for (LegInput leg : input.legs()) {
            // Carrier assignment (Load) for this leg.
            Job job = new Job();
            job.setEmployer(employer);
            // Rate × hours is the fallback basis; quantity-priced legs may omit
            // them, so default to zero (Job requires non-null values).
            job.setEstimatedDurationHours(leg.estimatedDurationHours() != null ? leg.estimatedDurationHours() : 0.0);
            job.setRatePerHour(leg.ratePerHour() != null ? leg.ratePerHour() : BigDecimal.ZERO);
            job.setRequiredLicenceCategory(leg.requiredLicenceCategory());
            job.setCurrency(currency);
            job.setStatus(JobStatus.OPEN);
            job = jobRepository.save(job);

            // The physical leg, attached to the itinerary at its sequence.
            Shipment shipment = new Shipment();
            shipment.setEmployer(employer);
            shipment.setMode(leg.mode() != null ? leg.mode() : Shipment.Mode.ROAD);
            shipment.setStatus(Shipment.ShipmentStatus.PLANNED);
            shipment.setCurrency(currency);
            shipment.setItinerary(itinerary);
            shipment.setLegSequence(seq);
            shipment.setOriginCountry(CountryCodes.normalize(leg.pickupCountry()));
            shipment.setDestinationCountry(CountryCodes.normalize(leg.deliveryCountry()));
            shipment.setDistanceKm(leg.distanceKm());
            shipment.setWeightKg(leg.weightKg());
            shipment.setVolumeM3(leg.volumeM3());
            shipment.setContainerCount(leg.containerCount());
            shipment.setPieceCount(leg.pieceCount());
            shipment = shipmentRepository.save(shipment);

            persistPickupDelivery(shipment, leg.pickupLocation(), leg.pickupCountry(),
                    leg.deliveryLocation(), leg.deliveryCountry(), input.dateNeeded());

            ShipmentLine line = new ShipmentLine();
            line.setShipment(shipment);
            line.setOrder(order);
            shipmentLineRepository.save(line);

            job.setShipment(shipment);
            jobRepository.save(job);

            // Price this leg now (carrier cost + per-mode commission on the leg).
            pricingService.priceJob(job);

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
        }
    }

    /** Legacy single-pickup / single-delivery path used by seed data and any
     *  client that hasn't migrated to the stops list yet. */
    private void persistLegacyPickupDelivery(Shipment shipment, TmsOrderInput input) {
        persistPickupDelivery(shipment, input.pickupLocation(), input.pickupCountry(),
                input.deliveryLocation(), input.deliveryCountry(), input.dateNeeded());
    }

    /** A single PICKUP + DELIVERY pair for one shipment leg. Shared by the
     *  legacy single-leg path and the intermodal builder. */
    private void persistPickupDelivery(Shipment shipment, String pickupName, String pickupCountry,
                                       String deliveryName, String deliveryCountry, LocalDate dateNeeded) {
        Location pickupLoc = upsertLocation(pickupName, pickupCountry);
        Location deliveryLoc = upsertLocation(deliveryName, deliveryCountry);

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
     *  back-filled so multiple jobs at the same name+country stay stable. */
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

    private TransportOrder.OrderStatus mapOrderStatus(JobStatus js) {
        switch (js) {
            case OPEN:        return TransportOrder.OrderStatus.NEW;
            case ASSIGNED:    return TransportOrder.OrderStatus.PLANNED;
            case IN_PROGRESS: return TransportOrder.OrderStatus.IN_EXECUTION;
            case COMPLETED:   return TransportOrder.OrderStatus.COMPLETED;
            case CANCELLED:   return TransportOrder.OrderStatus.CANCELLED;
            default:          return TransportOrder.OrderStatus.NEW;
        }
    }

    private Shipment.ShipmentStatus mapShipmentStatus(JobStatus js) {
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
