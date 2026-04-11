package com.driverdirect.service;

import com.driverdirect.dto.JobApplicationRequest;
import com.driverdirect.dto.JobApplicationResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;

import java.util.List;

public interface JobApplicationService {

    JobApplicationResponse applyForJob(Driver driver, Long jobId, JobApplicationRequest request);

    List<JobApplicationResponse> getApplicationsByDriver(Driver driver);

    List<JobApplicationResponse> getApplicationsForJob(Long jobId, Employer employer);

    JobApplicationResponse acceptApplication(Long applicationId, Employer employer);

    JobApplicationResponse rejectApplication(Long applicationId, Employer employer);

    JobApplicationResponse withdrawApplication(Long applicationId, Driver driver);
}
