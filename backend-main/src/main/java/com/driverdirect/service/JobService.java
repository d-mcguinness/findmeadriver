package com.driverdirect.service;

import com.driverdirect.dto.CreateIntermodalJobRequest;
import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.JobResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.JobStatus;

import java.util.List;

public interface JobService {

    JobResponse createJob(Shipper shipper, CreateJobRequest request);

    List<JobResponse> getJobsByShipper(Shipper shipper);

    JobResponse getJobById(Long id);

    List<JobResponse> getMatchingJobs(Driver driver);

    JobResponse updateJobStatus(Long jobId, Shipper shipper, JobStatus status);

    // ---- Intermodal (M2b) ----

    ItineraryResponse createIntermodalJob(Shipper shipper, CreateIntermodalJobRequest request);

    List<ItineraryResponse> getItinerariesByShipper(Shipper shipper);

    ItineraryResponse getItineraryById(Long id, Shipper shipper);
}
