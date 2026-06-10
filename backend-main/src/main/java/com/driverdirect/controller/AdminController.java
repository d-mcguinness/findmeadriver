package com.driverdirect.controller;

import com.driverdirect.dto.AdminUserResponse;
import com.driverdirect.dto.ComplianceDocumentResponse;
import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLoadRequest;
import com.driverdirect.dto.LoadApplicationRequest;
import com.driverdirect.dto.LoadApplicationResponse;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.LoadResponse;
import com.driverdirect.dto.LocationResponse;
import com.driverdirect.dto.OrderResponse;
import com.driverdirect.dto.PlatformStatsResponse;
import com.driverdirect.dto.ShipmentResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.User;
import com.driverdirect.repository.CarrierRepository;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.repository.LoadApplicationRepository;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.LoadRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.TransportOrderRepository;
import com.driverdirect.service.AdminService;
import com.driverdirect.service.ComplianceService;
import com.driverdirect.service.LoadApplicationService;
import com.driverdirect.service.LoadService;
import com.driverdirect.security.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private final AdminService adminService;
    private final JwtUtil jwtUtil;
    private final ComplianceService complianceService;
    private final LoadService loadService;
    private final LoadApplicationService loadApplicationService;
    private final LoadRepository loadRepository;
    private final LoadApplicationRepository loadApplicationRepository;
    private final CarrierRepository carrierRepository;
    private final ShipperRepository shipperRepository;
    private final TransportOrderRepository transportOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ItineraryRepository itineraryRepository;
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

    /**
     * Mint a login-shape token for an arbitrary user so an admin can browse the
     * app AS that user ("mimic"). Stateless: returns the SAME {message, user,
     * token} shape as POST /api/user/login, so the frontend reuses its login
     * flow. Guarded by /api/admin/** (ROLE_ADMIN); forbids mimicking yourself.
     */
    @PostMapping("/users/{id}/impersonate")
    public ResponseEntity<Map<String, Object>> impersonateUser(@PathVariable Long id, Authentication auth) {
        User target = adminService.getUserById(id);
        if (target.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new IllegalArgumentException("You cannot mimic yourself");
        }
        log.info("IMPERSONATION: admin {} is now mimicking user {} (id={})",
                auth.getName(), target.getEmail(), target.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successful");
        response.put("user", target);
        response.put("token", jwtUtil.generateToken(target));
        return ResponseEntity.ok(response);
    }

    // ---- Platform Stats ----

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = new PlatformStatsResponse();
        stats.setTotalUsers(adminService.getAllUsers().size());
        stats.setTotalCarriers(carrierRepository.count());
        stats.setTotalShippers(shipperRepository.count());

        List<Load> allLoads = loadRepository.findAll();
        stats.setTotalLoads(allLoads.size());
        stats.setOpenLoads(allLoads.stream().filter(j -> j.getStatus() == LoadStatus.OPEN).count());
        stats.setAssignedLoads(allLoads.stream().filter(j -> j.getStatus() == LoadStatus.ASSIGNED).count());
        stats.setInProgressLoads(allLoads.stream().filter(j -> j.getStatus() == LoadStatus.IN_PROGRESS).count());
        stats.setCompletedLoads(allLoads.stream().filter(j -> j.getStatus() == LoadStatus.COMPLETED).count());
        stats.setCancelledLoads(allLoads.stream().filter(j -> j.getStatus() == LoadStatus.CANCELLED).count());
        stats.setPendingDocuments(complianceService.getPendingDocuments().size());

        return ResponseEntity.ok(stats);
    }

    // ---- Loads ----

    @GetMapping("/shippers")
    public ResponseEntity<List<Map<String, Object>>> getAllShippers() {
        List<Map<String, Object>> response = shipperRepository.findAll().stream()
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

    @GetMapping("/carriers")
    public ResponseEntity<List<Map<String, Object>>> getAllCarriers() {
        List<Map<String, Object>> response = carrierRepository.findAll().stream()
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

    @PostMapping("/loads")
    public ResponseEntity<LoadResponse> createLoadAsAdmin(
            @RequestParam Long shipperId,
            @RequestBody CreateLoadRequest request) {
        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Shipper not found: " + shipperId));
        return ResponseEntity.status(HttpStatus.CREATED).body(loadService.createLoad(shipper, request));
    }

    @GetMapping("/loads")
    public ResponseEntity<List<LoadResponse>> getAllLoads() {
        List<Load> loads = loadRepository.findAll();
        List<LoadResponse> response = loads.stream()
                .map(load -> LoadResponse.from(load, loadApplicationRepository.findByLoad(load).size()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/loads/{id}")
    public ResponseEntity<LoadResponse> getLoad(@PathVariable Long id) {
        return ResponseEntity.ok(loadService.getLoadById(id));
    }

    @PutMapping("/loads/{id}")
    public ResponseEntity<LoadResponse> updateLoadAsAdmin(
            @PathVariable Long id,
            @RequestBody CreateLoadRequest request) {
        return ResponseEntity.ok(loadService.updateLoad(id, null, request));
    }

    @GetMapping("/loads/{loadId}/applications")
    public ResponseEntity<List<LoadApplicationResponse>> getApplicationsForLoad(@PathVariable Long loadId) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found: " + loadId));
        List<LoadApplicationResponse> apps = loadApplicationRepository.findByLoad(load).stream()
                .map(LoadApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(apps);
    }

    /** Per-carrier eligibility to apply for this load — runs the real applyForLoad
     *  rules (status/duplicate/licence/availability/cabotage) so the admin UI
     *  only offers Apply where it would actually succeed. */
    @GetMapping("/loads/{loadId}/carrier-eligibility")
    public ResponseEntity<List<Map<String, Object>>> getCarrierEligibility(@PathVariable Long loadId) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found: " + loadId));
        List<Carrier> carriers = carrierRepository.findAll();
        Map<Long, LoadApplicationService.Eligibility> eligibility =
                loadApplicationService.checkEligibilityForCarriers(load, carriers);
        List<Map<String, Object>> response = carriers.stream()
                .map(d -> {
                    LoadApplicationService.Eligibility reason = eligibility.getOrDefault(
                            d.getId(), LoadApplicationService.Eligibility.OK);
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("carrierId", d.getId());
                    m.put("eligible", reason == LoadApplicationService.Eligibility.OK);
                    m.put("reason", reason.name());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/carriers/{carrierId}/applications")
    public ResponseEntity<List<LoadApplicationResponse>> getApplicationsForCarrier(@PathVariable Long carrierId) {
        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new IllegalArgumentException("Carrier not found: " + carrierId));
        List<LoadApplicationResponse> apps = loadApplicationRepository
                .findByCarrierOrderByAppliedAtDesc(carrier).stream()
                .map(LoadApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(apps);
    }

    @PostMapping("/applications")
    public ResponseEntity<LoadApplicationResponse> applyOnBehalfOfCarrier(
            @RequestParam Long carrierId,
            @RequestParam Long loadId,
            @RequestBody(required = false) LoadApplicationRequest request) {
        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new IllegalArgumentException("Carrier not found: " + carrierId));
        if (request == null) request = new LoadApplicationRequest();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadApplicationService.applyForLoad(carrier, loadId, request));
    }

    @PutMapping("/loads/{id}/cancel")
    public ResponseEntity<LoadResponse> cancelLoad(@PathVariable Long id) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));
        if (load.getStatus() == LoadStatus.COMPLETED || load.getStatus() == LoadStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot cancel a " + load.getStatus() + " load");
        }
        load.setStatus(LoadStatus.CANCELLED);
        load = loadRepository.save(load);
        return ResponseEntity.ok(LoadResponse.from(load, loadApplicationRepository.findByLoad(load).size()));
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

    @PostMapping("/itineraries")
    public ResponseEntity<ItineraryResponse> createItineraryAsAdmin(
            @RequestParam Long shipperId,
            @RequestBody CreateIntermodalLoadRequest request) {
        Shipper shipper = shipperRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Shipper not found: " + shipperId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadService.createIntermodalLoad(shipper, request));
    }

    @GetMapping("/itineraries")
    public ResponseEntity<List<ItineraryResponse>> getAllItineraries() {
        return ResponseEntity.ok(itineraryRepository.findAll().stream()
                .map(ItineraryResponse::from).toList());
    }

    @GetMapping("/itineraries/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(@PathVariable Long id) {
        return ResponseEntity.ok(itineraryRepository.findById(id)
                .map(ItineraryResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + id)));
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
