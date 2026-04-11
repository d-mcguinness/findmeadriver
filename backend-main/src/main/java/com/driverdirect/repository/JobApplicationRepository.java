package com.driverdirect.repository;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByDriverOrderByAppliedAtDesc(Driver driver);

    List<JobApplication> findByJob(Job job);

    List<JobApplication> findByJobAndStatus(Job job, ApplicationStatus status);

    Optional<JobApplication> findByJobAndDriver(Job job, Driver driver);

    boolean existsByJobAndDriver(Job job, Driver driver);
}
