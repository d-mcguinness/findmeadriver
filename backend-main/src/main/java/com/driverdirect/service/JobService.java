package com.driverdirect.service;

import com.driverdirect.dto.CreateIntermodalJobRequest;
import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.JobResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.JobStatus;

import java.util.List;

public interface JobService {

    JobResponse createJob(Employer employer, CreateJobRequest request);

    List<JobResponse> getJobsByEmployer(Employer employer);

    JobResponse getJobById(Long id);

    List<JobResponse> getMatchingJobs(Driver driver);

    JobResponse updateJobStatus(Long jobId, Employer employer, JobStatus status);

    // ---- Intermodal (M2b) ----

    ItineraryResponse createIntermodalJob(Employer employer, CreateIntermodalJobRequest request);

    List<ItineraryResponse> getItinerariesByEmployer(Employer employer);

    ItineraryResponse getItineraryById(Long id, Employer employer);
}
