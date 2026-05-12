package com.driverdirect.service;

import com.driverdirect.dto.CreateJobRequest;
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
            String currency) {

        public static TmsOrderInput fromRequest(CreateJobRequest req, Employer employer) {
            String c = req.getCurrency() != null ? req.getCurrency() : employer.getCurrency();
            String pc = req.getPickupCountry() != null ? req.getPickupCountry() : employer.getCountry();
            String dc = req.getDeliveryCountry() != null ? req.getDeliveryCountry() : employer.getCountry();
            return new TmsOrderInput(
                    req.getTitle(), req.getDescription(), req.getDateNeeded(),
                    req.getPickupLocation(), req.getDeliveryLocation(),
                    pc, dc, c);
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

        Location pickupLoc = upsertLocation(input.pickupLocation(), input.pickupCountry());
        Location deliveryLoc = upsertLocation(input.deliveryLocation(), input.deliveryCountry());

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

        ShipmentLine line = new ShipmentLine();
        line.setShipment(shipment);
        line.setOrder(order);
        shipmentLineRepository.save(line);

        job.setShipment(shipment);
        return shipment;
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
