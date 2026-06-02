package com.driverdirect.controller;

import com.driverdirect.dto.*;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.model.CarrierTimeSlot;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.CarrierAvailabilityRepository;
import com.driverdirect.repository.CarrierRepository;
import com.driverdirect.repository.CarrierTimeSlotRepository;
import com.driverdirect.service.AvailabilityService;
import com.driverdirect.service.CabotageService;
import com.driverdirect.service.CarrierLaneService;
import com.driverdirect.service.LoadApplicationService;
import com.driverdirect.service.LoadService;
import com.driverdirect.service.ComplianceService;
import com.driverdirect.service.RatingService;
import com.driverdirect.util.CountryCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/carrier")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class CarrierController {

    private final CarrierRepository carrierRepository;
    private final AvailabilityService availabilityService;
    private final LoadService loadService;
    private final LoadApplicationService applicationService;
    private final RatingService ratingService;
    private final ComplianceService complianceService;
    private final CarrierTimeSlotRepository timeSlotRepository;
    private final CarrierAvailabilityRepository availabilityRepository;
    private final CarrierLaneService carrierLaneService;
    private final CabotageService cabotageService;

    // ---- Capabilities (M4): the modes a carrier operates + its mode credentials ----

    @GetMapping("/capabilities")
    public ResponseEntity<CarrierCapabilitiesResponse> getCapabilities(Authentication auth) {
        return ResponseEntity.ok(CarrierCapabilitiesResponse.from(getCarrier(auth)));
    }

    @PutMapping("/capabilities")
    public ResponseEntity<CarrierCapabilitiesResponse> setCapabilities(
            Authentication auth,
            @RequestBody CarrierCapabilitiesRequest request) {
        Carrier carrier = getCarrier(auth);
        java.util.Set<Shipment.Mode> modes = new java.util.HashSet<>();
        if (request.getSupportedModes() != null) {
            for (String m : request.getSupportedModes()) {
                try { modes.add(Shipment.Mode.valueOf(m.trim().toUpperCase())); }
                catch (IllegalArgumentException ignored) { /* skip unknown mode */ }
            }
        }
        carrier.setSupportedModes(modes);
        carrier.setCredentials(request.getCredentials() != null
                ? new java.util.HashSet<>(request.getCredentials()) : new java.util.HashSet<>());
        carrierRepository.save(carrier);
        return ResponseEntity.ok(CarrierCapabilitiesResponse.from(carrier));
    }

    @PutMapping("/availability")
    public ResponseEntity<AvailabilityResponse> setWeeklyAvailability(
            Authentication auth,
            @RequestBody WeeklyAvailabilityRequest request) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(availabilityService.setWeeklyAvailability(carrier, request));
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Carrier carrier = getCarrier(auth);

        if (start == null) {
            start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (end == null) {
            end = start.plusDays(6);
        }

        return ResponseEntity.ok(availabilityService.getAvailability(carrier, start, end));
    }

    @GetMapping("/loads")
    public ResponseEntity<List<LoadResponse>> getMatchingLoads(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(loadService.getMatchingLoads(carrier));
    }

    @GetMapping("/loads/all")
    public ResponseEntity<List<LoadResponse>> getAllOpenLoads(Authentication auth) {
        getCarrier(auth); // validate carrier exists
        // Return all open loads regardless of availability match
        return ResponseEntity.ok(loadService.getMatchingLoads(getCarrier(auth)));
    }

    @PostMapping("/loads/{loadId}/apply")
    public ResponseEntity<LoadApplicationResponse> applyForLoad(
            Authentication auth,
            @PathVariable Long loadId,
            @RequestBody(required = false) LoadApplicationRequest request) {
        Carrier carrier = getCarrier(auth);
        if (request == null) {
            request = new LoadApplicationRequest();
        }
        return ResponseEntity.ok(applicationService.applyForLoad(carrier, loadId, request));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<LoadApplicationResponse>> getMyApplications(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(applicationService.getApplicationsByCarrier(carrier));
    }

    @PutMapping("/applications/{id}/withdraw")
    public ResponseEntity<LoadApplicationResponse> withdrawApplication(
            Authentication auth,
            @PathVariable Long id) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(applicationService.withdrawApplication(id, carrier));
    }

    @PostMapping("/loads/{loadId}/rate")
    public ResponseEntity<RatingResponse> rateShipper(
            Authentication auth,
            @PathVariable Long loadId,
            @RequestBody CreateRatingRequest request) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(ratingService.createRating(carrier, loadId, request));
    }

    @GetMapping("/loads/{loadId}/rated")
    public ResponseEntity<Boolean> hasRated(Authentication auth, @PathVariable Long loadId) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(ratingService.hasRated(carrier, loadId));
    }

    @GetMapping("/ratings")
    public ResponseEntity<UserRatingSummary> getMyRatings(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(ratingService.getRatingSummary(carrier.getId()));
    }

    // ---- Lanes (carrier-declared origin to destination country pairs) ----

    @GetMapping("/lanes")
    public ResponseEntity<List<CarrierLaneResponse>> getLanes(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(carrierLaneService.list(carrier));
    }

    @PostMapping("/lanes")
    public ResponseEntity<CarrierLaneResponse> addLane(
            Authentication auth,
            @RequestBody CarrierLaneRequest request) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(carrierLaneService.add(carrier, request));
    }

    @DeleteMapping("/lanes/{id}")
    public ResponseEntity<Void> deleteLane(Authentication auth, @PathVariable Long id) {
        Carrier carrier = getCarrier(auth);
        carrierLaneService.remove(carrier, id);
        return ResponseEntity.noContent().build();
    }

    // ---- Cabotage exposure + home country ----

    @GetMapping("/cabotage-exposure")
    public ResponseEntity<CabotageDashboardResponse> getCabotageExposure(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(CabotageDashboardResponse.of(
                carrier.getHomeCountry(), cabotageService.getExposure(carrier)));
    }

    @PutMapping("/home-country")
    public ResponseEntity<MessageResponse> setHomeCountry(
            Authentication auth, @RequestBody HomeCountryRequest request) {
        Carrier carrier = getCarrier(auth);
        carrier.setHomeCountry(CountryCodes.require(request.getCountry(), "country"));
        carrierRepository.save(carrier);
        return ResponseEntity.ok(new MessageResponse("Home country updated"));
    }

    @PostMapping("/compliance")
    public ResponseEntity<ComplianceDocumentResponse> addComplianceDocument(
            Authentication auth,
            @RequestBody CreateComplianceDocumentRequest request) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(complianceService.addDocument(carrier, request));
    }

    @GetMapping("/compliance")
    public ResponseEntity<CarrierComplianceSummary> getComplianceSummary(Authentication auth) {
        Carrier carrier = getCarrier(auth);
        return ResponseEntity.ok(complianceService.getComplianceSummary(carrier));
    }

    @DeleteMapping("/compliance/{id}")
    public ResponseEntity<Void> deleteComplianceDocument(
            Authentication auth,
            @PathVariable Long id) {
        Carrier carrier = getCarrier(auth);
        complianceService.deleteDocument(id, carrier);
        return ResponseEntity.noContent().build();
    }

    // ---- Time slots ----

    @GetMapping("/timeslots")
    public ResponseEntity<List<TimeSlotResponse>> getTimeSlots(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Carrier carrier = getCarrier(auth);
        List<TimeSlotResponse> slots = timeSlotRepository
                .findByCarrierAndDateBetweenOrderByDateAscStartTimeAsc(carrier, start, end)
                .stream().map(TimeSlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }

    @PostMapping("/timeslots")
    public ResponseEntity<TimeSlotResponse> addTimeSlot(
            Authentication auth,
            @RequestBody TimeSlotRequest request) {
        Carrier carrier = getCarrier(auth);
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("Slot end time must be after start time");
        }
        CarrierTimeSlot saved = timeSlotRepository.save(
                new CarrierTimeSlot(carrier, request.getDate(),
                        request.getStartTime(), request.getEndTime()));
        recomputeDayHours(carrier, request.getDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(TimeSlotResponse.from(saved));
    }

    @DeleteMapping("/timeslots/{id}")
    public ResponseEntity<Void> deleteTimeSlot(Authentication auth, @PathVariable Long id) {
        Carrier carrier = getCarrier(auth);
        CarrierTimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));
        if (!slot.getCarrier().getId().equals(carrier.getId())) {
            throw new IllegalArgumentException("Not your time slot");
        }
        LocalDate date = slot.getDate();
        timeSlotRepository.delete(slot);
        recomputeDayHours(carrier, date);
        return ResponseEntity.noContent().build();
    }

    private void recomputeDayHours(Carrier carrier, LocalDate date) {
        // Time slots are a road-driver convenience; they roll up into the ROAD duty clock.
        com.driverdirect.model.Shipment.Mode mode = com.driverdirect.model.Shipment.Mode.ROAD;
        List<CarrierTimeSlot> slots = timeSlotRepository
                .findByCarrierAndDateOrderByStartTimeAsc(carrier, date);
        double hours = slots.stream()
                .filter(s -> s.getMode() == mode)
                .mapToDouble(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0)
                .sum();
        CarrierAvailability existing = availabilityRepository.findByCarrierAndDateAndMode(carrier, date, mode)
                .orElse(null);
        if (existing != null) {
            existing.setAvailableHours(hours);
            availabilityRepository.save(existing);
        } else if (hours > 0) {
            availabilityRepository.save(new CarrierAvailability(carrier, date, mode, hours));
        }
    }

    private Carrier getCarrier(Authentication auth) {
        return carrierRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Carrier profile not found"));
    }
}
