package com.driverdirect.service;

import com.driverdirect.dto.JobApplicationRequest;
import com.driverdirect.dto.JobApplicationResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;

import java.util.List;

public interface JobApplicationService {

    /** Why a driver can or cannot apply for a job. OK means they may apply.
     *  applyForJob enforces exactly this; the admin UI previews it. */
    enum Eligibility { OK, JOB_NOT_OPEN, ALREADY_APPLIED, LICENCE, AVAILABILITY, CABOTAGE }

    /** Non-mutating preview of whether {@code driver} may apply for {@code job}. */
    Eligibility checkEligibility(Driver driver, Job job);

    JobApplicationResponse applyForJob(Driver driver, Long jobId, JobApplicationRequest request);

    List<JobApplicationResponse> getApplicationsByDriver(Driver driver);

    List<JobApplicationResponse> getApplicationsForJob(Long jobId, Employer employer);

    JobApplicationResponse acceptApplication(Long applicationId, Employer employer);

    JobApplicationResponse rejectApplication(Long applicationId, Employer employer);

    JobApplicationResponse withdrawApplication(Long applicationId, Driver driver);
}
