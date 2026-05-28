package com.driverdirect.service;

import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.CreateJobStopRequest;
import com.driverdirect.model.Customer;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.ShipmentLine;
import com.driverdirect.model.Stop;
import com.driverdirect.model.TransportOrder;
import com.driverdirect.repository.CustomerRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipmentLineRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.StopRepository;
import com.driverdirect.repository.TransportOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            List<TmsStopInput> stops) {

        // Legacy 8-arg constructor — seed data still calls it positionally.
        public TmsOrderInput(String title, String description,
                             java.time.LocalDate dateNeeded,
                             String pickupLocation, String deliveryLocation,
                             String pickupCountry, String deliveryCountry,
                             String currency) {
            this(title, description, dateNeeded, pickupLocation, deliveryLocation,
                    pickupCountry, deliveryCountry, currency, Collections.emptyList());
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
                pc = firstCountryOfType(stops, Stop.StopType.PICKUP, employer.getCountry());
                dc = lastCountryOfType(stops, Stop.StopType.DELIVERY, employer.getCountry());
            } else {
                pc = req.getPickupCountry() != null ? req.getPickupCountry() : employer.getCountry();
                dc = req.getDeliveryCountry() != null ? req.getDeliveryCountry() : employer.getCountry();
            }

            return new TmsOrderInput(
                    req.getTitle(), req.getDescription(), req.getDateNeeded(),
                    req.getPickupLocation(), req.getDeliveryLocation(),
                    pc, dc, c, stops);
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
        shipment.setMode(Shipment.Mode.ROAD);
        shipment.setStatus(mapShipmentStatus(job.getStatus()));
        shipment.setCurrency(input.currency());
        shipment.setOriginCountry(input.pickupCountry());
        shipment.setDestinationCountry(input.deliveryCountry());
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
        Location pickupLoc = upsertLocation(input.pickupLocation(), input.pickupCountry());
        Location deliveryLoc = upsertLocation(input.deliveryLocation(), input.deliveryCountry());

        if (pickupLoc != null) {
            Stop pickup = new Stop();
            pickup.setShipment(shipment);
            pickup.setSequence(1);
            pickup.setType(Stop.StopType.PICKUP);
            pickup.setLocation(pickupLoc);
            if (input.dateNeeded() != null) {
                pickup.setEarliestAt(input.dateNeeded().atTime(8, 0));
                pickup.setLatestAt(input.dateNeeded().atTime(11, 0));
            }
            stopRepository.save(pickup);
        }

        if (deliveryLoc != null) {
            Stop delivery = new Stop();
            delivery.setShipment(shipment);
            delivery.setSequence(2);
            delivery.setType(Stop.StopType.DELIVERY);
            delivery.setLocation(deliveryLoc);
            if (input.dateNeeded() != null) {
                delivery.setEarliestAt(input.dateNeeded().atTime(13, 0));
                delivery.setLatestAt(input.dateNeeded().atTime(18, 0));
            }
            stopRepository.save(delivery);
        }
    }

    private Location upsertLocation(String name, String country) {
        if (name == null || name.isBlank()) return null;
        String iso = country == null ? "IE" : country;
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
        String iso = s.country() == null ? "IE" : s.country();
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
