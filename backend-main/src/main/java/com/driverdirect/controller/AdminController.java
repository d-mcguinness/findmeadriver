package com.driverdirect.controller;

import com.driverdirect.dto.AdminUserResponse;
import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.JobResponse;
import com.driverdirect.dto.PlatformStatsResponse;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import com.driverdirect.model.User;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.EmployerRepository;
import com.driverdirect.repository.JobApplicationRepository;
import com.driverdirect.repository.JobRepository;
import com.driverdirect.service.AdminService;
import com.driverdirect.service.ComplianceService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private final AdminService adminService;
    private final ComplianceService complianceService;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final DriverRepository driverRepository;
    private final EmployerRepository employerRepository;

    // ---- Users ----

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        List<User> users = adminService.getAllUsers();
        List<AdminUserResponse> response = users.stream()
                .map(AdminUserResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable Long id) {
        User user = adminService.getUserById(id);
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }

    // ---- Platform Stats ----

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = new PlatformStatsResponse();
        stats.setTotalUsers(adminService.getAllUsers().size());
        stats.setTotalDrivers(driverRepository.count());
        stats.setTotalEmployers(employerRepository.count());

        List<Job> allJobs = jobRepository.findAll();
        stats.setTotalJobs(allJobs.size());
        stats.setOpenJobs(allJobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN).count());
        stats.setAssignedJobs(allJobs.stream().filter(j -> j.getStatus() == JobStatus.ASSIGNED).count());
        stats.setInProgressJobs(allJobs.stream().filter(j -> j.getStatus() == JobStatus.IN_PROGRESS).count());
        stats.setCompletedJobs(allJobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count());
        stats.setCancelledJobs(allJobs.stream().filter(j -> j.getStatus() == JobStatus.CANCELLED).count());
        stats.setPendingDocuments(complianceService.getPendingDocuments().size());

        return ResponseEntity.ok(stats);
    }

    // ---- Jobs ----

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        List<JobResponse> response = jobs.stream()
                .map(job -> JobResponse.from(job, jobApplicationRepository.findByJob(job).size()))
                .toList();
        return ResponseEntity.ok(response);
    }

    // ---- Compliance ----

    @GetMapping("/compliance/pending")
    public ResponseEntity<List<ComplianceDocumentResponse>> getPendingDocuments() {
        return ResponseEntity.ok(complianceService.getPendingDocuments());
    }

    @PutMapping("/compliance/{id}/verify")
    public ResponseEntity<ComplianceDocumentResponse> verifyDocument(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(complianceService.verifyDocument(id, notes));
    }
}
