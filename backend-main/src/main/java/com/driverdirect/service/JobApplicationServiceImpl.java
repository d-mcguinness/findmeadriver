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
import java.util.Map;
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
    private final CredentialMatcherRegistry credentialMatchers;

    @Override
    public Eligibility checkEligibility(Driver driver, Job job) {
        JobApplication existing = applicationRepository.findByJobAndDriver(job, driver).orElse(null);
        double hours = availabilityService.getAvailableHoursOnDate(driver, job.getDateNeeded());
        boolean cabotageBlocking = cabotageService.check(driver, job).isBlocking();
        return evaluate(job, existing, driver.getLicenceCategory(), hours, cabotageBlocking);
    }

    @Override
    public Map<Long, Eligibility> checkEligibilityForDrivers(Job job, List<Driver> drivers) {
        // Resolve every per-driver fact in a constant number of queries instead
        // of one set per driver (the previous N+1):
        //  - applications for the job (1 query; driverId via the lazy FK, no load)
        Map<Long, JobApplication> appByDriver = applicationRepository.findByJob(job).stream()
                .collect(Collectors.toMap(a -> a.getDriver().getId(), a -> a, (a, b) -> a));
        //  - availability on the job's date across all drivers (1 query)
        Map<Long, Double> hoursByDriver =
                availabilityService.getAvailableHoursForDrivers(drivers, job.getDateNeeded());
        //  - cabotage op counts per driver for the destination country (0–1 query)
        Map<Long, Integer> cabotageByDriver = cabotageService.countInWindowByDriver(drivers, job);

        Map<Long, Eligibility> out = new java.util.LinkedHashMap<>();
        for (Driver d : drivers) {
            Long id = d.getId();
            boolean cabotageBlocking =
                    cabotageService.isOverLimit(d, job, cabotageByDriver.getOrDefault(id, 0));
            out.put(id, evaluate(job, appByDriver.get(id), d.getLicenceCategory(),
                    hoursByDriver.getOrDefault(id, 0.0), cabotageBlocking));
        }
        return out;
    }

    // The single rule set, shared by the apply path, the single-driver check,
    // and the batch preview so they can never drift. Order matters: the first
    // failing gate wins. Takes resolved facts so it issues no queries itself.
    private Eligibility evaluate(Job job, JobApplication existing, String licenceCategory,
                                 double availableHours, boolean cabotageBlocking) {
        if (job.getStatus() != JobStatus.OPEN) return Eligibility.JOB_NOT_OPEN;
        // Active application (non-withdrawn) blocks re-applying; a withdrawn one
        // can be revived for a fresh attempt.
        if (existing != null && existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            return Eligibility.ALREADY_APPLIED;
        }
        // Credential gate, dispatched by mode: road uses the cross-regime
        // covers() lattice (e.g. C+E ≡ HGV class 1); non-road modes don't carry
        // a road-licence requirement (M1c).
        if (!credentialMatchers.satisfies(job.getMode(), licenceCategory, job.getRequiredLicenceCategory())) {
            return Eligibility.LICENCE;
        }
        if (availableHours < job.getEstimatedDurationHours()) return Eligibility.AVAILABILITY;
        if (cabotageBlocking) return Eligibility.CABOTAGE;
        return Eligibility.OK;
    }

    @Override
    @Transactional
    public JobApplicationResponse applyForJob(Driver driver, Long jobId, JobApplicationRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        JobApplication existing = applicationRepository.findByJobAndDriver(job, driver).orElse(null);
        Double available = availabilityService.getAvailableHoursOnDate(driver, job.getDateNeeded());
        CabotageService.CabotageCheck cab = cabotageService.check(driver, job);

        // Enforce the same rule the admin UI previews (via evaluate); craft the
        // rich, value-bearing message for the gate that failed — reusing the
        // facts above so no check is recomputed.
        switch (evaluate(job, existing, driver.getLicenceCategory(), available, cab.isBlocking())) {
            case JOB_NOT_OPEN ->
                throw new IllegalArgumentException("This job is no longer accepting applications");
            case ALREADY_APPLIED ->
                throw new IllegalArgumentException("You have already applied for this job");
            case LICENCE ->
                throw new IllegalArgumentException("Your licence category does not satisfy the job requirement");
            case AVAILABILITY ->
                throw new IllegalArgumentException(
                        "You need " + job.getEstimatedDurationHours() + " available hours on " +
                        job.getDateNeeded() + " but only have " + available + "h set");
            case CABOTAGE ->
                throw new IllegalArgumentException(
                        "Cabotage limit reached for " + cab.country() + ": "
                                + cab.opsInWindow() + " of " + cab.limit()
                                + " ops in the last 7 days.");
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
