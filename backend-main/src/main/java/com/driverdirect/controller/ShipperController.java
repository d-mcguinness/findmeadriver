package com.driverdirect.controller;

import com.driverdirect.dto.*;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.service.LoadApplicationService;
import com.driverdirect.service.LoadService;
import com.driverdirect.service.RatingService;
import com.driverdirect.service.RoutePlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipper")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ShipperController {

    private final ShipperRepository shipperRepository;
    private final LoadService loadService;
    private final LoadApplicationService applicationService;
    private final RatingService ratingService;
    private final RoutePlannerService routePlannerService;

    @PostMapping("/loads")
    public ResponseEntity<LoadResponse> createLoad(
            Authentication auth,
            @RequestBody CreateLoadRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(loadService.createLoad(shipper, request));
    }

    @GetMapping("/loads")
    public ResponseEntity<List<LoadResponse>> getMyLoads(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.getLoadsByShipper(shipper));
    }

    @GetMapping("/loads/{id}")
    public ResponseEntity<LoadResponse> getLoad(Authentication auth, @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.getLoadById(id, shipper));
    }

    @PutMapping("/loads/{id}")
    public ResponseEntity<LoadResponse> updateLoad(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody CreateLoadRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.updateLoad(id, shipper, request));
    }

    // ---- Routing engine: propose door-to-door options (com.driverdirect.routing) ----

    /**
     * Propose Pareto-best (cost, CO2) routes for a cargo between two known
     * locations. Read-only planning — nothing is persisted; the shipper then
     * accepts an option and posts its legs through the existing itinerary
     * flow. Unknown location / same origin-destination / missing earliestReady
     * surface as 400 via {@link GlobalExceptionHandler}; an unreachable
     * destination is an empty list, not an error.
     */
    @PostMapping("/route-options")
    public ResponseEntity<List<RouteOptionResponse>> planRoutes(
            Authentication auth,
            @RequestBody RouteQueryRequest request) {
        Shipper shipper = getShipper(auth);
        // Tenant-scoped: a shipper can only route between public reference
        // nodes and its own locations (see RoutePlannerService).
        return ResponseEntity.ok(routePlannerService.planRoutesForShipper(request.toQuery(), shipper));
    }

    // ---- Intermodal (M2b): post a multi-leg movement ----

    @PostMapping("/itineraries")
    public ResponseEntity<ItineraryResponse> createItinerary(
            Authentication auth,
            @RequestBody CreateIntermodalLoadRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadService.createIntermodalLoad(shipper, request));
    }

    @GetMapping("/itineraries")
    public ResponseEntity<List<ItineraryResponse>> getMyItineraries(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.getItinerariesByShipper(shipper));
    }

    @GetMapping("/itineraries/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(Authentication auth, @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.getItineraryById(id, shipper));
    }

    @PutMapping("/itineraries/{id}")
    public ResponseEntity<ItineraryResponse> updateItinerary(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody CreateIntermodalLoadRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.updateIntermodalLoad(id, shipper, request));
    }

    @PutMapping("/itineraries/{id}/cancel")
    public ResponseEntity<ItineraryResponse> cancelItinerary(Authentication auth, @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.cancelItinerary(id, shipper));
    }

    @PutMapping("/loads/{id}/status")
    public ResponseEntity<LoadResponse> updateLoadStatus(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Shipper shipper = getShipper(auth);
        LoadStatus status = LoadStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(loadService.updateLoadStatus(id, shipper, status));
    }

    @PutMapping("/loads/{id}/cancel")
    public ResponseEntity<LoadResponse> cancelLoad(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(loadService.updateLoadStatus(id, shipper, LoadStatus.CANCELLED));
    }

    @GetMapping("/loads/{id}/applications")
    public ResponseEntity<List<LoadApplicationResponse>> getApplicationsForLoad(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.getApplicationsForLoad(id, shipper));
    }

    @PutMapping("/applications/{id}/accept")
    public ResponseEntity<LoadApplicationResponse> acceptApplication(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.acceptApplication(id, shipper));
    }

    @PutMapping("/applications/{id}/reject")
    public ResponseEntity<LoadApplicationResponse> rejectApplication(
            Authentication auth,
            @PathVariable Long id) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(applicationService.rejectApplication(id, shipper));
    }

    @PostMapping("/loads/{loadId}/rate")
    public ResponseEntity<RatingResponse> rateCarrier(
            Authentication auth,
            @PathVariable Long loadId,
            @RequestBody CreateRatingRequest request) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.createRating(shipper, loadId, request));
    }

    @GetMapping("/loads/{loadId}/rated")
    public ResponseEntity<Boolean> hasRated(Authentication auth, @PathVariable Long loadId) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.hasRated(shipper, loadId));
    }

    @GetMapping("/ratings")
    public ResponseEntity<UserRatingSummary> getMyRatings(Authentication auth) {
        Shipper shipper = getShipper(auth);
        return ResponseEntity.ok(ratingService.getRatingSummary(shipper.getId()));
    }

    private Shipper getShipper(Authentication auth) {
        return shipperRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Shipper profile not found"));
    }
}
