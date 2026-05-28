package com.driverdirect.controller;

import com.driverdirect.dto.*;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverAvailability;
import com.driverdirect.model.DriverTimeSlot;
import com.driverdirect.repository.DriverAvailabilityRepository;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.DriverTimeSlotRepository;
import com.driverdirect.service.AvailabilityService;
import com.driverdirect.service.CabotageService;
import com.driverdirect.service.DriverLaneService;
import com.driverdirect.service.JobApplicationService;
import com.driverdirect.service.JobService;
import com.driverdirect.service.ComplianceService;
import com.driverdirect.service.RatingService;
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
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class DriverController {

    private final DriverRepository driverRepository;
    private final AvailabilityService availabilityService;
    private final JobService jobService;
    private final JobApplicationService applicationService;
    private final RatingService ratingService;
    private final ComplianceService complianceService;
    private final DriverTimeSlotRepository timeSlotRepository;
    private final DriverAvailabilityRepository availabilityRepository;
    private final DriverLaneService driverLaneService;
    private final CabotageService cabotageService;

    @PutMapping("/availability")
    public ResponseEntity<AvailabilityResponse> setWeeklyAvailability(
            Authentication auth,
            @RequestBody WeeklyAvailabilityRequest request) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(availabilityService.setWeeklyAvailability(driver, request));
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Driver driver = getDriver(auth);

        if (start == null) {
            start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (end == null) {
            end = start.plusDays(6);
        }

        return ResponseEntity.ok(availabilityService.getAvailability(driver, start, end));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getMatchingJobs(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(jobService.getMatchingJobs(driver));
    }

    @GetMapping("/jobs/all")
    public ResponseEntity<List<JobResponse>> getAllOpenJobs(Authentication auth) {
        getDriver(auth); // validate driver exists
        // Return all open jobs regardless of availability match
        return ResponseEntity.ok(jobService.getMatchingJobs(getDriver(auth)));
    }

    @PostMapping("/jobs/{jobId}/apply")
    public ResponseEntity<JobApplicationResponse> applyForJob(
            Authentication auth,
            @PathVariable Long jobId,
            @RequestBody(required = false) JobApplicationRequest request) {
        Driver driver = getDriver(auth);
        if (request == null) {
            request = new JobApplicationRequest();
        }
        return ResponseEntity.ok(applicationService.applyForJob(driver, jobId, request));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<JobApplicationResponse>> getMyApplications(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(applicationService.getApplicationsByDriver(driver));
    }

    @PutMapping("/applications/{id}/withdraw")
    public ResponseEntity<JobApplicationResponse> withdrawApplication(
            Authentication auth,
            @PathVariable Long id) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(applicationService.withdrawApplication(id, driver));
    }

    @PostMapping("/jobs/{jobId}/rate")
    public ResponseEntity<RatingResponse> rateEmployer(
            Authentication auth,
            @PathVariable Long jobId,
            @RequestBody CreateRatingRequest request) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(ratingService.createRating(driver, jobId, request));
    }

    @GetMapping("/jobs/{jobId}/rated")
    public ResponseEntity<Boolean> hasRated(Authentication auth, @PathVariable Long jobId) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(ratingService.hasRated(driver, jobId));
    }

    @GetMapping("/ratings")
    public ResponseEntity<UserRatingSummary> getMyRatings(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(ratingService.getRatingSummary(driver.getId()));
    }

    // ---- Lanes (driver-declared origin to destination country pairs) ----

    @GetMapping("/lanes")
    public ResponseEntity<List<DriverLaneResponse>> getLanes(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(driverLaneService.list(driver));
    }

    @PostMapping("/lanes")
    public ResponseEntity<DriverLaneResponse> addLane(
            Authentication auth,
            @RequestBody DriverLaneRequest request) {
        Driver driver = getDriver(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(driverLaneService.add(driver, request));
    }

    @DeleteMapping("/lanes/{id}")
    public ResponseEntity<Void> deleteLane(Authentication auth, @PathVariable Long id) {
        Driver driver = getDriver(auth);
        driverLaneService.remove(driver, id);
        return ResponseEntity.noContent().build();
    }

    // ---- Cabotage exposure + home country ----

    @GetMapping("/cabotage-exposure")
    public ResponseEntity<CabotageDashboardResponse> getCabotageExposure(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(CabotageDashboardResponse.of(
                driver.getHomeCountry(), cabotageService.getExposure(driver)));
    }

    @PutMapping("/home-country")
    public ResponseEntity<MessageResponse> setHomeCountry(
            Authentication auth, @RequestBody HomeCountryRequest request) {
        Driver driver = getDriver(auth);
        if (request.getCountry() == null || request.getCountry().trim().length() != 2) {
            throw new IllegalArgumentException("country must be a 2-letter ISO code");
        }
        driver.setHomeCountry(request.getCountry().trim().toUpperCase());
        driverRepository.save(driver);
        return ResponseEntity.ok(new MessageResponse("Home country updated"));
    }

    @PostMapping("/compliance")
    public ResponseEntity<ComplianceDocumentResponse> addComplianceDocument(
            Authentication auth,
            @RequestBody CreateComplianceDocumentRequest request) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(complianceService.addDocument(driver, request));
    }

    @GetMapping("/compliance")
    public ResponseEntity<DriverComplianceSummary> getComplianceSummary(Authentication auth) {
        Driver driver = getDriver(auth);
        return ResponseEntity.ok(complianceService.getComplianceSummary(driver));
    }

    @DeleteMapping("/compliance/{id}")
    public ResponseEntity<Void> deleteComplianceDocument(
            Authentication auth,
            @PathVariable Long id) {
        Driver driver = getDriver(auth);
        complianceService.deleteDocument(id, driver);
        return ResponseEntity.noContent().build();
    }

    // ---- Time slots ----

    @GetMapping("/timeslots")
    public ResponseEntity<List<TimeSlotResponse>> getTimeSlots(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Driver driver = getDriver(auth);
        List<TimeSlotResponse> slots = timeSlotRepository
                .findByDriverAndDateBetweenOrderByDateAscStartTimeAsc(driver, start, end)
                .stream().map(TimeSlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }

    @PostMapping("/timeslots")
    public ResponseEntity<TimeSlotResponse> addTimeSlot(
            Authentication auth,
            @RequestBody TimeSlotRequest request) {
        Driver driver = getDriver(auth);
        if (request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("Slot end time must be after start time");
        }
        DriverTimeSlot saved = timeSlotRepository.save(
                new DriverTimeSlot(driver, request.getDate(),
                        request.getStartTime(), request.getEndTime()));
        recomputeDayHours(driver, request.getDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(TimeSlotResponse.from(saved));
    }

    @DeleteMapping("/timeslots/{id}")
    public ResponseEntity<Void> deleteTimeSlot(Authentication auth, @PathVariable Long id) {
        Driver driver = getDriver(auth);
        DriverTimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));
        if (!slot.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException("Not your time slot");
        }
        LocalDate date = slot.getDate();
        timeSlotRepository.delete(slot);
        recomputeDayHours(driver, date);
        return ResponseEntity.noContent().build();
    }

    private void recomputeDayHours(Driver driver, LocalDate date) {
        List<DriverTimeSlot> slots = timeSlotRepository
                .findByDriverAndDateOrderByStartTimeAsc(driver, date);
        double hours = slots.stream()
                .mapToDouble(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0)
                .sum();
        DriverAvailability existing = availabilityRepository.findByDriverAndDate(driver, date)
                .orElse(null);
        if (existing != null) {
            existing.setAvailableHours(hours);
            availabilityRepository.save(existing);
        } else if (hours > 0) {
            availabilityRepository.save(new DriverAvailability(driver, date, hours));
        }
    }

    private Driver getDriver(Authentication auth) {
        return driverRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found"));
    }
}
