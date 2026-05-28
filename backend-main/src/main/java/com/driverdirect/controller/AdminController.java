package com.driverdirect.controller;

import com.driverdirect.dto.AdminUserResponse;
import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateJobRequest;
import com.driverdirect.dto.JobApplicationRequest;
import com.driverdirect.dto.JobApplicationResponse;
import com.driverdirect.dto.JobResponse;
import com.driverdirect.dto.LocationResponse;
import com.driverdirect.dto.OrderResponse;
import com.driverdirect.dto.PlatformStatsResponse;
import com.driverdirect.dto.ShipmentResponse;
import com.driverdirect.model.Driver;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobStatus;
import com.driverdirect.model.User;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.EmployerRepository;
import com.driverdirect.repository.JobApplicationRepository;
import com.driverdirect.repository.JobRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.TransportOrderRepository;
import com.driverdirect.service.AdminService;
import com.driverdirect.service.ComplianceService;
import com.driverdirect.service.JobApplicationService;
import com.driverdirect.service.JobService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final DriverRepository driverRepository;
    private final EmployerRepository employerRepository;
    private final TransportOrderRepository transportOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final LocationRepository locationRepository;

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

    @GetMapping("/employers")
    public ResponseEntity<List<Map<String, Object>>> getAllEmployers() {
        List<Map<String, Object>> response = employerRepository.findAll().stream()
                .map(e -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("companyName", e.getCompanyName());
                    m.put("email", e.getEmail());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drivers")
    public ResponseEntity<List<Map<String, Object>>> getAllDrivers() {
        List<Map<String, Object>> response = driverRepository.findAll().stream()
                .map(d -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("firstName", d.getFirstName());
                    m.put("lastName", d.getLastName());
                    m.put("email", d.getEmail());
                    m.put("licenceCategory", d.getLicenceCategory());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJobAsAdmin(
            @RequestParam Long employerId,
            @RequestBody CreateJobRequest request) {
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new IllegalArgumentException("Employer not found: " + employerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(employer, request));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        List<JobResponse> response = jobs.stream()
                .map(job -> JobResponse.from(job, jobApplicationRepository.findByJob(job).size()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drivers/{driverId}/applications")
    public ResponseEntity<List<JobApplicationResponse>> getApplicationsForDriver(@PathVariable Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
        List<JobApplicationResponse> apps = jobApplicationRepository
                .findByDriverOrderByAppliedAtDesc(driver).stream()
                .map(JobApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(apps);
    }

    @PostMapping("/applications")
    public ResponseEntity<JobApplicationResponse> applyOnBehalfOfDriver(
            @RequestParam Long driverId,
            @RequestParam Long jobId,
            @RequestBody(required = false) JobApplicationRequest request) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
        if (request == null) request = new JobApplicationRequest();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobApplicationService.applyForJob(driver, jobId, request));
    }

    @PutMapping("/jobs/{id}/cancel")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot cancel a " + job.getStatus() + " job");
        }
        job.setStatus(JobStatus.CANCELLED);
        job = jobRepository.save(job);
        return ResponseEntity.ok(JobResponse.from(job, jobApplicationRepository.findByJob(job).size()));
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

    // ---- TMS read-only endpoints (Phase 0 step 5) ----

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(transportOrderRepository.findAll().stream()
                .map(OrderResponse::from).toList());
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(transportOrderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id)));
    }

    @GetMapping("/shipments")
    public ResponseEntity<List<ShipmentResponse>> getAllShipments() {
        return ResponseEntity.ok(shipmentRepository.findAll().stream()
                .map(ShipmentResponse::from).toList());
    }

    @GetMapping("/shipments/{id}")
    public ResponseEntity<ShipmentResponse> getShipment(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentRepository.findById(id)
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + id)));
    }

    @GetMapping("/locations")
    public ResponseEntity<List<LocationResponse>> getAllLocations() {
        return ResponseEntity.ok(locationRepository.findAll().stream()
                .map(LocationResponse::from).toList());
    }

    @GetMapping("/locations/{id}")
    public ResponseEntity<LocationResponse> getLocation(@PathVariable Long id) {
        return ResponseEntity.ok(locationRepository.findById(id)
                .map(LocationResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + id)));
    }
}
