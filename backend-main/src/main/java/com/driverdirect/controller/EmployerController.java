package com.driverdirect.controller;

import com.driverdirect.dto.*;
import com.driverdirect.model.Employer;
import com.driverdirect.model.JobStatus;
import com.driverdirect.repository.EmployerRepository;
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
@RequestMapping("/api/employer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class EmployerController {

    private final EmployerRepository employerRepository;
    private final JobService jobService;
    private final JobApplicationService applicationService;
    private final RatingService ratingService;

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJob(
            Authentication auth,
            @RequestBody CreateJobRequest request) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(employer, request));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getMyJobs(Authentication auth) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(jobService.getJobsByEmployer(employer));
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
        Employer employer = getEmployer(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createIntermodalJob(employer, request));
    }

    @GetMapping("/itineraries")
    public ResponseEntity<List<ItineraryResponse>> getMyItineraries(Authentication auth) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(jobService.getItinerariesByEmployer(employer));
    }

    @GetMapping("/itineraries/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(Authentication auth, @PathVariable Long id) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(jobService.getItineraryById(id, employer));
    }

    @PutMapping("/jobs/{id}/status")
    public ResponseEntity<JobResponse> updateJobStatus(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Employer employer = getEmployer(auth);
        JobStatus status = JobStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(jobService.updateJobStatus(id, employer, status));
    }

    @PutMapping("/jobs/{id}/cancel")
    public ResponseEntity<JobResponse> cancelJob(
            Authentication auth,
            @PathVariable Long id) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(jobService.updateJobStatus(id, employer, JobStatus.CANCELLED));
    }

    @GetMapping("/jobs/{id}/applications")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForJob(
            Authentication auth,
            @PathVariable Long id) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(applicationService.getApplicationsForJob(id, employer));
    }

    @PutMapping("/applications/{id}/accept")
    public ResponseEntity<JobApplicationResponse> acceptApplication(
            Authentication auth,
            @PathVariable Long id) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(applicationService.acceptApplication(id, employer));
    }

    @PutMapping("/applications/{id}/reject")
    public ResponseEntity<JobApplicationResponse> rejectApplication(
            Authentication auth,
            @PathVariable Long id) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(applicationService.rejectApplication(id, employer));
    }

    @PostMapping("/jobs/{jobId}/rate")
    public ResponseEntity<RatingResponse> rateDriver(
            Authentication auth,
            @PathVariable Long jobId,
            @RequestBody CreateRatingRequest request) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(ratingService.createRating(employer, jobId, request));
    }

    @GetMapping("/jobs/{jobId}/rated")
    public ResponseEntity<Boolean> hasRated(Authentication auth, @PathVariable Long jobId) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(ratingService.hasRated(employer, jobId));
    }

    @GetMapping("/ratings")
    public ResponseEntity<UserRatingSummary> getMyRatings(Authentication auth) {
        Employer employer = getEmployer(auth);
        return ResponseEntity.ok(ratingService.getRatingSummary(employer.getId()));
    }

    private Employer getEmployer(Authentication auth) {
        return employerRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Employer profile not found"));
    }
}
