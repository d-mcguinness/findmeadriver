package com.driverdirect.service;

import com.driverdirect.dto.DriverLaneRequest;
import com.driverdirect.dto.DriverLaneResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverLane;
import com.driverdirect.repository.DriverLaneRepository;
import com.driverdirect.util.CountryCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverLaneService {

    private final DriverLaneRepository repository;

    public List<DriverLaneResponse> list(Driver driver) {
        return repository.findByDriverOrderByOriginCountryAscDestinationCountryAsc(driver).stream()
                .map(DriverLaneResponse::from)
                .collect(Collectors.toList());
    }

    /** Internal raw fetch used by the job-matching filter. */
    public List<DriverLane> findAllForDriver(Driver driver) {
        return repository.findByDriverOrderByOriginCountryAscDestinationCountryAsc(driver);
    }

    @Transactional
    public DriverLaneResponse add(Driver driver, DriverLaneRequest request) {
        String origin = normaliseCountry(request.getOriginCountry(), "origin");
        String destination = normaliseCountry(request.getDestinationCountry(), "destination");

        // Same-country lanes (e.g. IE → IE for purely domestic) are allowed —
        // they're how a driver opts in to UK-internal or France-internal work
        // without surfacing cross-border jobs.

        return repository.findByDriverAndOriginCountryAndDestinationCountry(driver, origin, destination)
                .map(DriverLaneResponse::from)
                .orElseGet(() -> {
                    DriverLane lane = new DriverLane();
                    lane.setDriver(driver);
                    lane.setOriginCountry(origin);
                    lane.setDestinationCountry(destination);
                    return DriverLaneResponse.from(repository.save(lane));
                });
    }

    @Transactional
    public void remove(Driver driver, Long id) {
        DriverLane lane = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lane not found"));
        if (!lane.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException("Lane belongs to another driver");
        }
        repository.delete(lane);
    }

    private String normaliseCountry(String raw, String which) {
        return CountryCodes.require(raw, which + " country");
    }
}
