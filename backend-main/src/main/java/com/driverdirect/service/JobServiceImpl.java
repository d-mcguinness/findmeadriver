package com.driverdirect.service;

import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.JobResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverLane;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import com.driverdirect.model.LicenceCategory;
import com.driverdirect.repository.JobApplicationRepository;
import com.driverdirect.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final AvailabilityService availabilityService;
    private final TmsTreeService tmsTreeService;
    private final DriverLaneService driverLaneService;
    private final CabotageService cabotageService;

    @Override
    @Transactional
    public JobResponse createJob(Employer employer, CreateJobRequest request) {
        // Load-level fields only — customer-facing metadata lives on the tree.
        Job job = new Job();
        job.setEmployer(employer);
        job.setEstimatedDurationHours(request.getEstimatedDurationHours());
        job.setRatePerHour(request.getRatePerHour());
        job.setRequiredLicenceCategory(request.getRequiredLicenceCategory());
        String currency = request.getCurrency() != null
                ? request.getCurrency()
                : (employer.getCurrency() != null ? employer.getCurrency() : "EUR");
        job.setCurrency(currency);
        job.setStatus(JobStatus.OPEN);
        job = jobRepository.save(job);

        // Compose the TMS tree around the bare Job. Country defaults inherit
        // from the employer unless the request explicitly overrides.
        tmsTreeService.createTreeFor(job, TmsTreeService.TmsOrderInput.fromRequest(request, employer));
        job = jobRepository.save(job);
        return JobResponse.from(job, 0);
    }

    @Override
    public List<JobResponse> getJobsByEmployer(Employer employer) {
        return jobRepository.findByEmployerOrderByCreatedAtDesc(employer).stream()
                .map(job -> JobResponse.from(job, applicationRepository.findByJob(job).size()))
                .collect(Collectors.toList());
    }

    @Override
    public JobResponse getJobById(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        return JobResponse.from(job, applicationRepository.findByJob(job).size());
    }

    @Override
    public List<JobResponse> getMatchingJobs(Driver driver) {
        // Browse must agree with apply-time validation, which matches licences
        // through the LicenceCategory.covers() lattice (e.g. a C+E holder may
        // take a C job; UK HGV class 1 ≡ EU C+E) rather than exact category
        // equality. So fetch all OPEN jobs and apply the same satisfies() check
        // JobApplicationServiceImpl uses — otherwise a driver sees only
        // exact-match jobs and misses ones they're actually entitled to apply
        // for (and a null-licence driver was previously shown jobs they can't).
        List<Job> jobs = jobRepository.findByStatusOrderByDateNeededAsc(JobStatus.OPEN);
        String have = driver.getLicenceCategory();

        // Lane filter: when the driver has configured at least one (origin →
        // destination) country pair, restrict matches to jobs on those lanes.
        // Drivers with no lanes see everything (existing behaviour).
        List<DriverLane> lanes = driverLaneService.findAllForDriver(driver);

        // Licence + lane are pure in-memory predicates; apply them first so the
        // two DB lookups below run only over the surviving candidate set.
        List<Job> candidates = jobs.stream()
                .filter(job -> LicenceCategory.satisfies(have, job.getRequiredLicenceCategory()))
                .filter(job -> matchesAnyLane(job, lanes))
                .collect(Collectors.toList());

        // Batch the per-job lookups: availability for all candidate dates in one
        // query, application counts for all candidates in one query — instead of
        // two queries per job (N+1).
        Set<LocalDate> dates = candidates.stream()
                .map(Job::getDateNeeded).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<LocalDate, Double> hoursByDate = availabilityService.getAvailableHoursForDates(driver, dates);
        Map<Long, Integer> applicationCounts = applicationCountsByJobId(candidates);

        return candidates.stream()
                .filter(job -> {
                    double available = job.getDateNeeded() == null ? 0.0
                            : hoursByDate.getOrDefault(job.getDateNeeded(), 0.0);
                    return available >= job.getEstimatedDurationHours();
                })
                .map(job -> JobResponse.from(job, applicationCounts.getOrDefault(job.getId(), 0)))
                .collect(Collectors.toList());
    }

    /** Application counts keyed by job id, fetched in a single grouped query. */
    private Map<Long, Integer> applicationCountsByJobId(List<Job> jobs) {
        if (jobs.isEmpty()) return Map.of();
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : applicationRepository.countByJobIn(jobs)) {
            counts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    private boolean matchesAnyLane(Job job, List<DriverLane> lanes) {
        if (lanes.isEmpty()) return true;
        String origin = job.getPickupCountry();
        String destination = job.getDeliveryCountry();
        // Job without country metadata (no Shipment yet) doesn't match any lane.
        if (origin == null || destination == null) return false;
        for (DriverLane lane : lanes) {
            if (Objects.equals(origin, lane.getOriginCountry())
                    && Objects.equals(destination, lane.getDestinationCountry())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public JobResponse updateJobStatus(Long jobId, Employer employer, JobStatus status) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (!job.getEmployer().getId().equals(employer.getId())) {
            throw new IllegalArgumentException("You can only update your own jobs");
        }

        validateStatusTransition(job.getStatus(), status);
        job.setStatus(status);
        job = jobRepository.save(job);

        // Log a cabotage row when work is marked complete and the trip
        // qualifies. No-op for jobs that aren't cabotage (handled inside).
        if (status == JobStatus.COMPLETED && job.getAssignedDriver() != null) {
            cabotageService.recordIfApplicable(job.getAssignedDriver(), job);
        }
        return JobResponse.from(job, applicationRepository.findByJob(job).size());
    }

    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
            JobStatus.OPEN, Set.of(JobStatus.CANCELLED),
            JobStatus.ASSIGNED, Set.of(JobStatus.IN_PROGRESS, JobStatus.CANCELLED),
            JobStatus.IN_PROGRESS, Set.of(JobStatus.COMPLETED),
            JobStatus.COMPLETED, Set.of(),
            JobStatus.CANCELLED, Set.of()
    );

    private void validateStatusTransition(JobStatus current, JobStatus target) {
        Set<JobStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalArgumentException(
                    "Cannot change status from " + current + " to " + target);
        }
    }
}
