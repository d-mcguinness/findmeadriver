package com.driverdirect.repository;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByDriverOrderByAppliedAtDesc(Driver driver);

    List<JobApplication> findByJob(Job job);

    /** Application counts for a set of jobs in one query: rows of [jobId, count]. */
    @Query("select ja.job.id, count(ja) from JobApplication ja where ja.job in :jobs group by ja.job.id")
    List<Object[]> countByJobIn(@Param("jobs") List<Job> jobs);

    List<JobApplication> findByJobAndStatus(Job job, ApplicationStatus status);

    Optional<JobApplication> findByJobAndDriver(Job job, Driver driver);

    boolean existsByJobAndDriver(Job job, Driver driver);
}
