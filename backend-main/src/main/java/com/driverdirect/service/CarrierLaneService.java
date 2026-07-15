package com.driverdirect.service;

import com.driverdirect.dto.CarrierLaneRequest;
import com.driverdirect.dto.CarrierLaneResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.util.CountryCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarrierLaneService {

    private final CarrierLaneRepository repository;
    private final LocationRepository locationRepository;

    public List<CarrierLaneResponse> list(Carrier carrier) {
        return repository.findByCarrierOrderByOriginCountryAscDestinationCountryAsc(carrier).stream()
                .map(CarrierLaneResponse::from)
                .collect(Collectors.toList());
    }

    /** Internal raw fetch used by the load-matching filter. */
    public List<CarrierLane> findAllForCarrier(Carrier carrier) {
        return repository.findByCarrierOrderByOriginCountryAscDestinationCountryAsc(carrier);
    }

    @Transactional
    public CarrierLaneResponse add(Carrier carrier, CarrierLaneRequest request) {
        String origin = normaliseCountry(request.getOriginCountry(), "origin");
        String destination = normaliseCountry(request.getDestinationCountry(), "destination");

        // Same-country lanes (e.g. IE → IE for purely domestic) are allowed —
        // they're how a carrier opts in to UK-internal or France-internal work
        // without surfacing cross-border loads.

        // One lane per (carrier, origin, destination): re-posting the same
        // pair upserts the timetable onto the existing lane rather than
        // failing, so a carrier can add or update a schedule in place.
        CarrierLane lane = repository
                .findByCarrierAndOriginCountryAndDestinationCountry(carrier, origin, destination)
                .orElseGet(() -> {
                    CarrierLane created = new CarrierLane();
                    created.setCarrier(carrier);
                    created.setOriginCountry(origin);
                    created.setDestinationCountry(destination);
                    return created;
                });

        applyTimetable(lane, request);
        return CarrierLaneResponse.from(repository.save(lane));
    }

    @Transactional
    public void remove(Carrier carrier, Long id) {
        CarrierLane lane = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lane not found"));
        if (!lane.getCarrier().getId().equals(carrier.getId())) {
            throw new IllegalArgumentException("Lane belongs to another carrier");
        }
        repository.delete(lane);
    }

    /** Validate + write the optional timetable. A request with no timetable
     *  fields leaves any existing schedule untouched (a plain re-add of the
     *  country pair never wipes a schedule); a partial timetable is rejected
     *  rather than half-stored. */
    private void applyTimetable(CarrierLane lane, CarrierLaneRequest request) {
        boolean anyProvided = request.getDepartureDays() != null || request.getDepartureTime() != null
                || request.getTransitDurationHours() != null || request.getServiceMode() != null;
        if (!anyProvided) return;

        boolean allCore = request.getDepartureDays() != null && !request.getDepartureDays().isEmpty()
                && request.getDepartureTime() != null && request.getTransitDurationHours() != null;
        if (!allCore) {
            throw new IllegalArgumentException(
                    "A timetable needs departureDays, departureTime and transitDurationHours together");
        }
        double transitHours = request.getTransitDurationHours();
        if (!Double.isFinite(transitHours) || transitHours <= 0
                || transitHours > CarrierLane.MAX_TRANSIT_HOURS) {
            throw new IllegalArgumentException("transitDurationHours must be positive and at most "
                    + (long) CarrierLane.MAX_TRANSIT_HOURS);
        }

        // Strict on input (unlike the fail-soft stored-value getter): an
        // unrecognised day name is a client error worth surfacing.
        String days = request.getDepartureDays().stream()
                .map(d -> {
                    try {
                        return DayOfWeek.valueOf(d.trim().toUpperCase()).name();
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Unrecognised departure day: " + d);
                    }
                })
                .distinct()
                .collect(Collectors.joining(","));

        // A scheduled service is one concrete mode. INTERMODAL is a derived,
        // multi-leg-only label; PARCEL has no routing-graph support yet
        // (TransferPolicy pairs no terminal with it), so both are rejected —
        // a timetabled service is ROAD, RAIL, OCEAN or AIR.
        Shipment.Mode mode;
        try {
            mode = Shipment.Mode.valueOf(request.getServiceMode() == null
                    ? "" : request.getServiceMode().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("A timetable needs a serviceMode (RAIL, OCEAN, AIR or ROAD)");
        }
        if (mode == Shipment.Mode.INTERMODAL || mode == Shipment.Mode.PARCEL) {
            throw new IllegalArgumentException(
                    "serviceMode must be ROAD, RAIL, OCEAN or AIR — one concrete, routable mode per service");
        }

        lane.setServiceMode(mode);
        lane.setDepartureDays(days);
        lane.setDepartureTime(request.getDepartureTime());
        lane.setTransitDurationHours(request.getTransitDurationHours());
        lane.setOriginLocation(resolveLocation(request.getOriginLocationId(), "originLocationId"));
        lane.setDestinationLocation(resolveLocation(request.getDestinationLocationId(), "destinationLocationId"));
    }

    private Location resolveLocation(Long id, String field) {
        if (id == null) return null;
        return locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(field + " not found: " + id));
    }

    private String normaliseCountry(String raw, String which) {
        return CountryCodes.require(raw, which + " country");
    }
}
