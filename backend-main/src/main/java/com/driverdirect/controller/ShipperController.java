package com.driverdirect.controller;

import com.driverdirect.dto.*;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.JobStatus;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.service.JobApplicationService;
import com.driverdirect.service.JobService;
import com.driverdirect.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipper")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ShipperController {

    private final ShipperRepository shipperRepository;
    private final JobService jobService;
    private final JobApplicationService applicationService;
    private final RatingService ratingService;

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJob(
            Authentication auth,
            @RequestBody CreateJobRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(shipper, request));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getMyJobs(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(jobService.getJobsByShipper(shipper));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    // ---- Intermodal (M2b): post a multi-leg movement ----

    @PostMapping("/itineraries")
    public ResponseEntity<ItineraryResponse> createItinerary(
            Authentication auth,
            @RequestBody CreateIntermodalJobRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createIntermodalJob(shipper, request));
    }

    @GetMapping("/itineraries")
    public ResponseEntity<List<ItineraryResponse>> getMyItineraries(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(jobService.getItinerariesByShipper(shipper));
    }

    @GetMapping("/itineraries/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(Authentication auth, @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(jobService.getItineraryById(id, shipper));
    }

    @PutMapping("/jobs/{id}/status")
    public ResponseEntity<JobResponse> updateJobStatus(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Shipper shipper = getShipper(auth);
        JobStatus status = JobStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(jobService.updateJobStatus(id, shipper, status));
    }

    @PutMapping("/jobs/{id}/cancel")
    public ResponseEntity<JobResponse> cancelJob(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(jobService.updateJobStatus(id, shipper, JobStatus.CANCELLED));
    }

    @GetMapping("/jobs/{id}/applications")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForJob(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.getApplicationsForJob(id, shipper));
    }

    @PutMapping("/applications/{id}/accept")
    public ResponseEntity<JobApplicationResponse> acceptApplication(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.acceptApplication(id, shipper));
    }

    @PutMapping("/applications/{id}/reject")
    public ResponseEntity<JobApplicationResponse> rejectApplication(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.rejectApplication(id, shipper));
    }

    @PostMapping("/jobs/{jobId}/rate")
    public ResponseEntity<RatingResponse> rateDriver(
            Authentication auth,
            @PathVariable Long jobId,
            @RequestBody CreateRatingRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.createRating(shipper, jobId, request));
    }

    @GetMapping("/jobs/{jobId}/rated")
    public ResponseEntity<Boolean> hasRated(Authentication auth, @PathVariable Long jobId) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.hasRated(shipper, jobId));
    }

    @GetMapping("/ratings")
    public ResponseEntity<UserRatingSummary> getMyRatings(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.getRatingSummary(shipper.getId()));
    }

    private Shipper getShipper(Authentication auth) {
        return shipperRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Shipper profile not found"));
    }
}
