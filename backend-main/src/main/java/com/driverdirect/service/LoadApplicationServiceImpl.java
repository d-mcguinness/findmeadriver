package com.driverdirect.service;

import com.driverdirect.dto.LoadApplicationRequest;
import com.driverdirect.dto.LoadApplicationResponse;
import com.driverdirect.model.*;
import com.driverdirect.repository.CarrierRepository;
import com.driverdirect.repository.LoadApplicationRepository;
import com.driverdirect.repository.LoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoadApplicationServiceImpl implements LoadApplicationService {

    private final LoadApplicationRepository applicationRepository;
    private final LoadRepository loadRepository;
    private final AvailabilityService availabilityService;
    private final RatingService ratingService;
    private final ComplianceService complianceService;
    private final CabotageService cabotageService;
    private final CredentialMatcherRegistry credentialMatchers;
    private final CarrierRepository carrierRepository;

    @Override
    public Eligibility checkEligibility(Carrier carrier, Load load) {
        LoadApplication existing = applicationRepository.findByLoadAndCarrier(load, carrier).orElse(null);
        double hours = availabilityService.getAvailableHoursOnDate(carrier, load.getDateNeeded());
        boolean cabotageBlocking = cabotageService.check(carrier, load).isBlocking();
        boolean modeOk = carrier.supportsMode(load.getMode());
        return evaluate(load, existing, carrier.getLicenceCategory(), modeOk, hours, cabotageBlocking);
    }

    @Override
    public Map<Long, Eligibility> checkEligibilityForCarriers(Load load, List<Carrier> carriers) {
        // Resolve every per-carrier fact in a constant number of queries instead
        // of one set per carrier (the previous N+1):
        //  - applications for the load (1 query; carrierId via the lazy FK, no load)
        Map<Long, LoadApplication> appByCarrier = applicationRepository.findByLoad(load).stream()
                .collect(Collectors.toMap(a -> a.getCarrier().getId(), a -> a, (a, b) -> a));
        //  - availability on the load's date across all carriers (1 query)
        Map<Long, Double> hoursByCarrier =
                availabilityService.getAvailableHoursForCarriers(carriers, load.getDateNeeded());
        //  - cabotage op counts per carrier for the destination country (0–1 query)
        Map<Long, Integer> cabotageByCarrier = cabotageService.countInWindowByCarrier(carriers, load);
        //  - supported modes per carrier (1 query; carriers absent = road-only)
        Map<Long, java.util.Set<Shipment.Mode>> modesByCarrier = new java.util.HashMap<>();
        if (!carriers.isEmpty()) {
            for (Object[] row : carrierRepository.findSupportedModesByCarriers(carriers)) {
                modesByCarrier.computeIfAbsent((Long) row[0], k -> new java.util.HashSet<>())
                        .add((Shipment.Mode) row[1]);
            }
        }

        Map<Long, Eligibility> out = new java.util.LinkedHashMap<>();
        for (Carrier d : carriers) {
            Long id = d.getId();
            boolean modeOk = Carrier.supportsMode(modesByCarrier.get(id), load.getMode());
            boolean cabotageBlocking =
                    cabotageService.isOverLimit(d, load, cabotageByCarrier.getOrDefault(id, 0));
            out.put(id, evaluate(load, appByCarrier.get(id), d.getLicenceCategory(),
                    modeOk, hoursByCarrier.getOrDefault(id, 0.0), cabotageBlocking));
        }
        return out;
    }

    // The single rule set, shared by the apply path, the single-carrier check,
    // and the batch preview so they can never drift. Order matters: the first
    // failing gate wins. Takes resolved facts so it issues no queries itself.
    private Eligibility evaluate(Load load, LoadApplication existing, String licenceCategory,
                                 boolean carrierSupportsMode, double availableHours,
                                 boolean cabotageBlocking) {
        if (load.getStatus() != LoadStatus.OPEN) return Eligibility.LOAD_NOT_OPEN;
        // Active application (non-withdrawn) blocks re-applying; a withdrawn one
        // can be revived for a fresh attempt.
        if (existing != null && existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            return Eligibility.ALREADY_APPLIED;
        }
        // The carrier must operate this leg's transport mode at all (M4).
        if (!carrierSupportsMode) return Eligibility.MODE_UNSUPPORTED;
        // Credential gate, dispatched by mode: road uses the cross-regime
        // covers() lattice (e.g. C+E ≡ HGV class 1); non-road modes don't carry
        // a road-licence requirement (M1c).
        if (!credentialMatchers.satisfies(load.getMode(), licenceCategory, load.getRequiredLicenceCategory())) {
            return Eligibility.LICENCE;
        }
        if (availableHours < load.getEstimatedDurationHours()) return Eligibility.AVAILABILITY;
        if (cabotageBlocking) return Eligibility.CABOTAGE;
        return Eligibility.OK;
    }

    @Override
    @Transactional
    public LoadApplicationResponse applyForLoad(Carrier carrier, Long loadId, LoadApplicationRequest request) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));

        LoadApplication existing = applicationRepository.findByLoadAndCarrier(load, carrier).orElse(null);
        Double available = availabilityService.getAvailableHoursOnDate(carrier, load.getDateNeeded());
        CabotageService.CabotageCheck cab = cabotageService.check(carrier, load);

        // Enforce the same rule the admin UI previews (via evaluate); craft the
        // rich, value-bearing message for the gate that failed — reusing the
        // facts above so no check is recomputed.
        boolean modeOk = carrier.supportsMode(load.getMode());
        switch (evaluate(load, existing, carrier.getLicenceCategory(), modeOk, available, cab.isBlocking())) {
            case LOAD_NOT_OPEN ->
                throw new IllegalArgumentException("This load is no longer accepting applications");
            case ALREADY_APPLIED ->
                throw new IllegalArgumentException("You have already applied for this load");
            case MODE_UNSUPPORTED ->
                throw new IllegalArgumentException(
                        "You are not set up to carry " + load.getMode() + " loads");
            case LICENCE ->
                throw new IllegalArgumentException("Your licence category does not satisfy the load requirement");
            case AVAILABILITY ->
                throw new IllegalArgumentException(
                        "You need " + load.getEstimatedDurationHours() + " available hours on " +
                        load.getDateNeeded() + " but only have " + available + "h set");
            case CABOTAGE ->
                throw new IllegalArgumentException(
                        "Cabotage limit reached for " + cab.country() + ": "
                                + cab.opsInWindow() + " of " + cab.limit()
                                + " ops in the last 7 days.");
            default -> { /* OK — proceed */ }
        }

        LoadApplication application = existing != null ? existing : new LoadApplication();
        application.setLoad(load);
        application.setCarrier(carrier);
        application.setStatus(ApplicationStatus.PENDING);
        application.setCoverNote(request.getCoverNote());

        application = applicationRepository.save(application);
        return LoadApplicationResponse.from(application);
    }

    @Override
    public List<LoadApplicationResponse> getApplicationsByCarrier(Carrier carrier) {
        return applicationRepository.findByCarrierOrderByAppliedAtDesc(carrier).stream()
                .map(LoadApplicationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<LoadApplicationResponse> getApplicationsForLoad(Long loadId, Shipper shipper) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));

        if (!load.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only view applications for your own loads");
        }

        return applicationRepository.findByLoad(load).stream()
                .map(this::enrichResponse)
                .collect(Collectors.toList());
    }

    private LoadApplicationResponse enrichResponse(LoadApplication app) {
        Long carrierId = app.getCarrier().getId();
        Double avgRating = ratingService.getAverageRating(carrierId);
        Long ratingCount = ratingService.getRatingCount(carrierId);
        boolean verified = complianceService.isCarrierVerified(app.getCarrier());
        return LoadApplicationResponse.from(app, avgRating, ratingCount, verified);
    }

    @Override
    @Transactional
    public LoadApplicationResponse acceptApplication(Long applicationId, Shipper shipper) {
        LoadApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getLoad().getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only manage applications for your own loads");
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Can only accept pending applications");
        }

        // Accept this application
        application.setStatus(ApplicationStatus.ACCEPTED);
        applicationRepository.save(application);

        // Set load to ASSIGNED with the accepted carrier
        Load load = application.getLoad();
        load.setStatus(LoadStatus.ASSIGNED);
        load.setAssignedCarrier(application.getCarrier());
        loadRepository.save(load);

        // Reject all other pending applications for this load
        applicationRepository.findByLoadAndStatus(load, ApplicationStatus.PENDING)
                .forEach(other -> {
                    other.setStatus(ApplicationStatus.REJECTED);
                    applicationRepository.save(other);
                });

        return LoadApplicationResponse.from(application);
    }

    @Override
    @Transactional
    public LoadApplicationResponse rejectApplication(Long applicationId, Shipper shipper) {
        LoadApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getLoad().getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("You can only manage applications for your own loads");
        }

        application.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.save(application);
        return LoadApplicationResponse.from(application);
    }

    @Override
    @Transactional
    public LoadApplicationResponse withdrawApplication(Long applicationId, Carrier carrier) {
        LoadApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getCarrier().getId().equals(carrier.getId())) {
            throw new IllegalArgumentException("You can only withdraw your own applications");
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Can only withdraw pending applications");
        }

        application.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
        return LoadApplicationResponse.from(application);
    }
}
