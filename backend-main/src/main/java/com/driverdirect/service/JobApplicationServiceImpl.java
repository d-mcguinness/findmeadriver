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
    @Transactional
    public JobApplicationResponse applyForJob(Driver driver, Long jobId, JobApplicationRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalArgumentException("This job is no longer accepting applications");
        }

        // Active application (non-withdrawn) blocks re-applying; withdrawn applications
        // can be revived so the driver gets a fresh attempt.
        JobApplication existing = applicationRepository.findByJobAndDriver(job, driver).orElse(null);
        if (existing != null && existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("You have already applied for this job");
        }

        // Cross-regime equivalence: a CE driver satisfies an HGV_CLASS_1 job and
        // vice versa. Unknown category strings fall back to equality (see
        // LicenceCategory.satisfies).
        String required = job.getRequiredLicenceCategory();
        String have = driver.getLicenceCategory();
        if (!LicenceCategory.satisfies(have, required)) {
            throw new IllegalArgumentException("Your licence category does not satisfy the job requirement");
        }

        // Validate driver has enough available hours
        Double available = availabilityService.getAvailableHoursOnDate(driver, job.getDateNeeded());
        if (available < job.getEstimatedDurationHours()) {
            throw new IllegalArgumentException(
                    "You need " + job.getEstimatedDurationHours() + " available hours on " +
                    job.getDateNeeded() + " but only have " + available + "h set");
        }

        // Cabotage gate: a foreign driver may perform at most 3 cabotage ops
        // per host country per 7-day rolling window. HOME_COUNTRY_MISSING is
        // non-blocking — the driver should be prompted to set their base
        // country via a separate channel, but we don't refuse the apply.
        CabotageService.CabotageCheck cab = cabotageService.check(driver, job);
        if (cab.isBlocking()) {
            throw new IllegalArgumentException(
                    "Cabotage limit reached for " + cab.country() + ": "
                            + cab.opsInWindow() + " of " + cab.limit()
                            + " ops in the last 7 days.");
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
