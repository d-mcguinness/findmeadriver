package com.driverdirect.repository;

import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByEmployerOrderByCreatedAtDesc(Employer employer);

    List<Job> findByStatusAndRequiredCdlTypeOrderByDateNeededAsc(JobStatus status, Driver.CDLType cdlType);

    List<Job> findByStatusOrderByDateNeededAsc(JobStatus status);
}
