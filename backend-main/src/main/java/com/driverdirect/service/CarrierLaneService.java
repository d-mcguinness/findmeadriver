package com.driverdirect.service;

import com.driverdirect.dto.CarrierLaneRequest;
import com.driverdirect.dto.CarrierLaneResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierLane;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.util.CountryCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarrierLaneService {

    private final CarrierLaneRepository repository;

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

        return repository.findByCarrierAndOriginCountryAndDestinationCountry(carrier, origin, destination)
                .map(CarrierLaneResponse::from)
                .orElseGet(() -> {
                    CarrierLane lane = new CarrierLane();
                    lane.setCarrier(carrier);
                    lane.setOriginCountry(origin);
                    lane.setDestinationCountry(destination);
                    return CarrierLaneResponse.from(repository.save(lane));
                });
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

    private String normaliseCountry(String raw, String which) {
        return CountryCodes.require(raw, which + " country");
    }
}
