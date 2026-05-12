package com.driverdirect.repository;

import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByEmployerOrderByCreatedAtDesc(Employer employer);

    List<Job> findByStatusAndRequiredLicenceCategoryOrderByDateNeededAsc(JobStatus status, String licenceCategory);

    List<Job> findByStatusOrderByDateNeededAsc(JobStatus status);
}
