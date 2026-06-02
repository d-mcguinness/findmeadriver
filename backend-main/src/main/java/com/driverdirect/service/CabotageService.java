package com.driverdirect.service;

import com.driverdirect.dto.CabotageExposureResponse;
import com.driverdirect.model.CabotageOperation;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Load;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import com.driverdirect.repository.CabotageOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Cabotage compliance helper. See {@link CabotageOperation} for the
 * regulatory background and the simplification we apply.
 */
@Service
@RequiredArgsConstructor
public class CabotageService {

    /** EU Regulation 1072/2009 — max 3 cabotage ops per 7-day window. */
    public static final int LIMIT_PER_WINDOW = 3;
    public static final int WINDOW_DAYS = 7;

    private final CabotageOperationRepository repository;

    /**
     * Cabotage (EU 1072/2009) is a road-haulage concept. Air/sea/rail legs have
     * entirely different (or no) cabotage regimes, so every entry point below
     * short-circuits for non-road loads rather than relying on the
     * origin==destination test coincidentally holding.
     */
    private boolean isRoad(Load load) {
        return load != null && load.getMode() == Shipment.Mode.ROAD;
    }

    public enum CheckResult {
        OK,
        NOT_CABOTAGE,
        HOME_COUNTRY_MISSING,
        OVER_LIMIT
    }

    public record CabotageCheck(CheckResult result, String country, int opsInWindow, int limit) {
        public boolean isBlocking() {
            return result == CheckResult.OVER_LIMIT;
        }
    }

    /**
     * Decide whether {@code carrier} may apply for {@code load} on cabotage
     * grounds. Returns OK / NOT_CABOTAGE / HOME_COUNTRY_MISSING / OVER_LIMIT.
     * Only OVER_LIMIT is considered blocking (see {@link CabotageCheck#isBlocking}).
     */
    public CabotageCheck check(Carrier carrier, Load load) {
        String origin = load.getPickupCountry();
        String destination = load.getDeliveryCountry();
        if (!isRoad(load)) {
            // Non-road leg → cabotage doesn't apply; never blocks.
            return new CabotageCheck(CheckResult.NOT_CABOTAGE, destination, 0, LIMIT_PER_WINDOW);
        }
        if (origin == null || destination == null) {
            // No country metadata → can't classify; allow.
            return new CabotageCheck(CheckResult.NOT_CABOTAGE, destination, 0, LIMIT_PER_WINDOW);
        }
        // Cabotage requires a domestic move (origin == destination) inside a
        // country that is NOT the carrier's home country.
        if (!origin.equals(destination)) {
            return new CabotageCheck(CheckResult.NOT_CABOTAGE, destination, 0, LIMIT_PER_WINDOW);
        }
        if (carrier.getHomeCountry() == null || carrier.getHomeCountry().isBlank()) {
            return new CabotageCheck(CheckResult.HOME_COUNTRY_MISSING, destination, 0, LIMIT_PER_WINDOW);
        }
        if (carrier.getHomeCountry().equalsIgnoreCase(destination)) {
            return new CabotageCheck(CheckResult.NOT_CABOTAGE, destination, 0, LIMIT_PER_WINDOW);
        }

        LocalDate since = LocalDate.now().minusDays(WINDOW_DAYS);
        int count = repository.findByCarrierAndCountryAndPerformedAtGreaterThanEqual(
                carrier, destination.toUpperCase(), since).size();
        if (count >= LIMIT_PER_WINDOW) {
            return new CabotageCheck(CheckResult.OVER_LIMIT, destination, count, LIMIT_PER_WINDOW);
        }
        return new CabotageCheck(CheckResult.OK, destination, count, LIMIT_PER_WINDOW);
    }

    /**
     * Per-carrier op counts in the current window for {@code load}'s destination
     * country — but only when the load is a domestic (cabotage-relevant) move.
     * One query for the whole carrier set; empty map otherwise. Pair with
     * {@link #isOverLimit} to decide blocking without a per-carrier query.
     */
    public Map<Long, Integer> countInWindowByCarrier(Collection<Carrier> carriers, Load load) {
        String origin = load.getPickupCountry();
        String destination = load.getDeliveryCountry();
        if (!isRoad(load) || origin == null || destination == null
                || !origin.equals(destination) || carriers.isEmpty()) {
            return Map.of();
        }
        LocalDate since = LocalDate.now().minusDays(WINDOW_DAYS);
        Map<Long, Integer> counts = new HashMap<>();
        for (CabotageOperation op : repository
                .findByCarrierInAndCountryAndPerformedAtGreaterThanEqual(
                        carriers, destination.toUpperCase(), since)) {
            counts.merge(op.getCarrier().getId(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Blocking predicate for a pre-counted window — the OVER_LIMIT branch of
     * {@link #check} without the repository hit. Use with the count from
     * {@link #countInWindowByCarrier}.
     */
    public boolean isOverLimit(Carrier carrier, Load load, int opsInWindow) {
        if (!isRoad(load)) return false;
        String origin = load.getPickupCountry();
        String destination = load.getDeliveryCountry();
        if (origin == null || destination == null || !origin.equals(destination)) return false;
        String home = carrier.getHomeCountry();
        if (home == null || home.isBlank() || home.equalsIgnoreCase(destination)) return false;
        return opsInWindow >= LIMIT_PER_WINDOW;
    }

    /**
     * Persist a CabotageOperation if {@code load} qualifies as one for
     * {@code carrier}. Called when a Load transitions to COMPLETED.
     */
    @Transactional
    public Optional<CabotageOperation> recordIfApplicable(Carrier carrier, Load load) {
        if (carrier == null || load == null) return Optional.empty();
        if (!isRoad(load)) return Optional.empty();
        String origin = load.getPickupCountry();
        String destination = load.getDeliveryCountry();
        if (origin == null || destination == null) return Optional.empty();
        if (!origin.equals(destination)) return Optional.empty();
        if (carrier.getHomeCountry() == null
                || carrier.getHomeCountry().equalsIgnoreCase(destination)) return Optional.empty();

        CabotageOperation op = new CabotageOperation();
        op.setCarrier(carrier);
        op.setLoad(load);
        op.setCountry(destination.toUpperCase());
        // Provenance: the final unload point. country stays the authoritative
        // count key; this is just "where".
        op.setDeliveryLocation(finalDeliveryLocation(load));
        // Best available "performed" date: the load's dateNeeded (when the work
        // was scheduled). Falls back to today if missing.
        op.setPerformedAt(Objects.requireNonNullElse(load.getDateNeeded(), LocalDate.now()));
        return Optional.of(repository.save(op));
    }

    /**
     * One row per country the carrier has any activity in within the rolling
     * window — including countries with zero ops in the window (so the UI can
     * still surface the country with capacity = 3).
     */
    public List<CabotageExposureResponse> getExposure(Carrier carrier) {
        LocalDate windowStart = LocalDate.now().minusDays(WINDOW_DAYS);
        List<CabotageOperation> recent = repository
                .findByCarrierAndPerformedAtGreaterThanEqualOrderByCountryAscPerformedAtDesc(carrier, windowStart);
        Map<String, List<CabotageOperation>> byCountry = new TreeMap<>();
        for (CabotageOperation op : recent) {
            byCountry.computeIfAbsent(op.getCountry(), k -> new ArrayList<>()).add(op);
        }
        List<CabotageExposureResponse> out = new ArrayList<>();
        for (Map.Entry<String, List<CabotageOperation>> e : byCountry.entrySet()) {
            List<CabotageOperation> ops = e.getValue();
            // ops are PerformedAt-desc within a country (repository order), so
            // ops.get(0) is the newest.
            CabotageOperation newestOp = ops.get(0);
            LocalDate oldest = ops.get(ops.size() - 1).getPerformedAt();
            LocalDate newest = newestOp.getPerformedAt();
            String newestLocation = newestOp.getDeliveryLocation() != null
                    ? newestOp.getDeliveryLocation().getName() : null;
            out.add(CabotageExposureResponse.of(
                    e.getKey(), ops.size(), LIMIT_PER_WINDOW, windowStart,
                    oldest, newest, newestLocation));
        }
        return out;
    }

    /** Final unload Location for provenance — last DELIVERY stop on the load's
     *  shipment (stops are ordered by sequence). Null when no shipment/stop. */
    private Location finalDeliveryLocation(Load load) {
        if (load.getShipment() == null || load.getShipment().getStops() == null) return null;
        Location last = null;
        for (Stop s : load.getShipment().getStops()) {
            if (s.getType() == Stop.StopType.DELIVERY && s.getLocation() != null) {
                last = s.getLocation();
            }
        }
        return last;
    }
}
