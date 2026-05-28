package com.driverdirect.service;

import com.driverdirect.dto.JobApplicationRequest;
import com.driverdirect.dto.JobApplicationResponse;
import com.driverdirect.model.*;
import com.driverdirect.repository.JobApplicationRepository;
import com.driverdirect.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final AvailabilityService availabilityService;
    private final RatingService ratingService;
    private final ComplianceService complianceService;
    private final CabotageService cabotageService;

    @Override
    public Eligibility checkEligibility(Driver driver, Job job) {
        return checkEligibility(driver, job,
                applicationRepository.findByJobAndDriver(job, driver).orElse(null));
    }

    // The single rule set, shared by the apply path and the admin preview so
    // they can never drift. Order matters: the first failing gate is returned.
    private Eligibility checkEligibility(Driver driver, Job job, JobApplication existing) {
        if (job.getStatus() != JobStatus.OPEN) return Eligibility.JOB_NOT_OPEN;
        // Active application (non-withdrawn) blocks re-applying; a withdrawn one
        // can be revived for a fresh attempt.
        if (existing != null && existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            return Eligibility.ALREADY_APPLIED;
        }
        // Cross-regime equivalence via the covers() lattice (e.g. C+E ≡ HGV class 1).
        if (!LicenceCategory.satisfies(driver.getLicenceCategory(), job.getRequiredLicenceCategory())) {
            return Eligibility.LICENCE;
        }
        if (availabilityService.getAvailableHoursOnDate(driver, job.getDateNeeded())
                < job.getEstimatedDurationHours()) {
            return Eligibility.AVAILABILITY;
        }
        // Cabotage: foreign driver capped at 3 ops per host country per 7 days.
        // HOME_COUNTRY_MISSING is non-blocking (handled inside isBlocking()).
        if (cabotageService.check(driver, job).isBlocking()) return Eligibility.CABOTAGE;
        return Eligibility.OK;
    }

    @Override
    @Transactional
    public JobApplicationResponse applyForJob(Driver driver, Long jobId, JobApplicationRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        JobApplication existing = applicationRepository.findByJobAndDriver(job, driver).orElse(null);

        // Enforce the same eligibility the admin UI previews; craft the rich,
        // value-bearing message for the gate that failed.
        switch (checkEligibility(driver, job, existing)) {
            case JOB_NOT_OPEN ->
                throw new IllegalArgumentException("This job is no longer accepting applications");
            case ALREADY_APPLIED ->
                throw new IllegalArgumentException("You have already applied for this job");
            case LICENCE ->
                throw new IllegalArgumentException("Your licence category does not satisfy the job requirement");
            case AVAILABILITY -> {
                Double available = availabilityService.getAvailableHoursOnDate(driver, job.getDateNeeded());
                throw new IllegalArgumentException(
                        "You need " + job.getEstimatedDurationHours() + " available hours on " +
                        job.getDateNeeded() + " but only have " + available + "h set");
            }
            case CABOTAGE -> {
                CabotageService.CabotageCheck cab = cabotageService.check(driver, job);
                throw new IllegalArgumentException(
                        "Cabotage limit reached for " + cab.country() + ": "
                                + cab.opsInWindow() + " of " + cab.limit()
                                + " ops in the last 7 days.");
            }
            default -> { /* OK — proceed */ }
        }

        JobApplication application = existing != null ? existing : new JobApplication();
        application.setJob(job);
        application.setDriver(driver);
        application.setStatus(ApplicationStatus.PENDING);
        application.setCoverNote(request.getCoverNote());

        application = applicationRepository.save(application);
        return JobApplicationResponse.from(application);
    }

    @Override
    public List<JobApplicationResponse> getApplicationsByDriver(Driver driver) {
        return applicationRepository.findByDriverOrderByAppliedAtDesc(driver).stream()
                .map(JobApplicationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<JobApplicationResponse> getApplicationsForJob(Long jobId, Employer employer) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (!job.getEmployer().getId().equals(employer.getId())) {
            throw new IllegalArgumentException("You can only view applications for your own jobs");
        }

        return applicationRepository.findByJob(job).stream()
                .map(this::enrichResponse)
                .collect(Collectors.toList());
    }

    private JobApplicationResponse enrichResponse(JobApplication app) {
        Long driverId = app.getDriver().getId();
        Double avgRating = ratingService.getAverageRating(driverId);
        Long ratingCount = ratingService.getRatingCount(driverId);
        boolean verified = complianceService.isDriverVerified(app.getDriver());
        return JobApplicationResponse.from(app, avgRating, ratingCount, verified);
    }

    @Override
    @Transactional
    public JobApplicationResponse acceptApplication(Long applicationId, Employer employer) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getJob().getEmployer().getId().equals(employer.getId())) {
            throw new IllegalArgumentException("You can only manage applications for your own jobs");
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Can only accept pending applications");
        }

        // Accept this application
        application.setStatus(ApplicationStatus.ACCEPTED);
        applicationRepository.save(application);

        // Set job to ASSIGNED with the accepted driver
        Job job = application.getJob();
        job.setStatus(JobStatus.ASSIGNED);
        job.setAssignedDriver(application.getDriver());
        jobRepository.save(job);

        // Reject all other pending applications for this job
        applicationRepository.findByJobAndStatus(job, ApplicationStatus.PENDING)
                .forEach(other -> {
                    other.setStatus(ApplicationStatus.REJECTED);
                    applicationRepository.save(other);
                });

        return JobApplicationResponse.from(application);
    }

    @Override
    @Transactional
    public JobApplicationResponse rejectApplication(Long applicationId, Employer employer) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getJob().getEmployer().getId().equals(employer.getId())) {
            throw new IllegalArgumentException("You can only manage applications for your own jobs");
        }

        application.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.save(application);
        return JobApplicationResponse.from(application);
    }

    @Override
    @Transactional
    public JobApplicationResponse withdrawApplication(Long applicationId, Driver driver) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (!application.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException("You can only withdraw your own applications");
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Can only withdraw pending applications");
        }

        application.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
        return JobApplicationResponse.from(application);
    }
}
