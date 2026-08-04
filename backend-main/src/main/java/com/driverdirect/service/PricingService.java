package com.driverdirect.service;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.HandlingCharge;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Load;
import com.driverdirect.model.Location;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.Stop;
import com.driverdirect.repository.HandlingChargeRepository;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Computes the money on a Load's Shipment leg (M1b):
 * <pre>
 *   carrierCost   = ratePerHour × estimatedDurationHours   (what the carrier earns)
 *   commission    = carrierCost × commissionPercent(mode)  (the platform's mode-dependent cut)
 *   shipperTotal = carrierCost + commission               (what the shipper pays)
 * </pre>
 *
 * <p>The per-mode commission is the "charge accordingly" mechanism — it varies
 * by {@link Shipment.Mode} via {@link PricingPolicy}. The carrier-cost <em>basis</em>
 * is still hours × rate for every mode; per-mode bases (per-km / per-kg /
 * per-container) arrive in M3 once distance/weight/volume are persisted. The
 * commission percentage is snapshotted onto the Shipment so later rate changes
 * never rewrite historical charges.
 *
 * <p>All money is {@link BigDecimal} at 2dp, HALF_UP — never double.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPolicy pricingPolicy;
    private final ShipmentRepository shipmentRepository;
    private final ItineraryRepository itineraryRepository;
    private final StopRepository stopRepository;
    private final HandlingChargeRepository handlingChargeRepository;
    private final TransferPolicy transferPolicy;

    /**
     * Prices the leg of {@code load} and persists the result onto its Shipment.
     * No-op for a Load not yet linked to a Shipment.
     */
    @Transactional
    public void priceLoad(Load load) {
        if (load == null || load.getShipment() == null) return;
        // Re-load the leg in THIS transaction. The passed Load may be detached
        // (e.g. the non-transactional seed path), where its lazy @ManyToOne
        // shipment is an uninitialised proxy on a closed session. getId() on a
        // proxy is safe (no init); findById then gives us a managed entity.
        Shipment shipment = shipmentRepository.findById(load.getShipment().getId()).orElse(null);
        if (shipment == null) return;

        Shipment.Mode mode = shipment.getMode() != null ? shipment.getMode() : Shipment.Mode.ROAD;

        // Carrier cost: price on the mode's rate-card basis when the leg carries
        // the quantity that basis needs (km / containers / chargeable-kg /
        // pieces); otherwise fall back to the carrier's rate × hours.
        PricingPolicy.RateCard card = pricingPolicy.rateCardFor(mode);
        BigDecimal qty = card != null ? chargeableQuantity(card.unit(), shipment) : null;
        BigDecimal carrierCost;
        if (card != null && qty != null && qty.signum() > 0) {
            BigDecimal metered = card.baseFee().add(card.ratePerUnit().multiply(qty));
            carrierCost = scale(metered.max(card.minimumCharge()));
            shipment.setChargeUnit(card.unit());
            shipment.setChargeableQuantity(scale(qty));
        } else {
            carrierCost = scale(hourlyCostOf(load));
            shipment.setChargeUnit(ChargeUnit.PER_HOUR);
            shipment.setChargeableQuantity(hoursOf(load));
        }

        BigDecimal pct = pricingPolicy.commissionPercentFor(mode);
        BigDecimal commission = scale(carrierCost.multiply(pct)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        shipment.setTotalRate(carrierCost);
        shipment.setCommissionPercent(pct);
        shipment.setCommissionAmount(commission);
        shipment.setShipperTotal(carrierCost.add(commission));
        shipmentRepository.save(shipment);
    }

    /**
     * Roll the already-priced legs of an intermodal Itinerary up into its
     * carrier / commission / grand totals (M2). Each leg ({@link Shipment}) must
     * have been priced via {@link #priceLoad} first. Mixed currencies across legs
     * are rejected for now — FX is out of scope.
     */
    @Transactional
    public void recalcItinerary(Itinerary itinerary) {
        if (itinerary == null || itinerary.getId() == null) return;
        List<Shipment> legs = shipmentRepository.findByItineraryOrderByLegSequenceAsc(itinerary);

        BigDecimal carrier = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;
        for (Shipment leg : legs) {
            if (leg.getCurrency() != null && itinerary.getCurrency() != null
                    && !leg.getCurrency().equals(itinerary.getCurrency())) {
                throw new IllegalArgumentException(
                        "Mixed-currency itinerary not supported yet: leg " + leg.getId()
                                + " is " + leg.getCurrency() + " but the itinerary is "
                                + itinerary.getCurrency());
            }
            carrier = carrier.add(nz(leg.getTotalRate()));
            commission = commission.add(nz(leg.getCommissionAmount()));
            grand = grand.add(nz(leg.getShipperTotal()));
        }

        // Terminal handling between the legs, derived from TransferPolicy — the
        // same rates the route planner quotes, so an accepted route's bill
        // matches its quote instead of coming out short by the handling.
        BigDecimal handling = recalcHandling(itinerary, legs);

        itinerary.setCarrierCostTotal(scale(carrier));
        itinerary.setCommissionTotal(scale(commission));
        itinerary.setHandlingTotal(scale(handling));
        itinerary.setGrandTotal(scale(grand.add(handling)));
        if (!legs.isEmpty()) {
            itinerary.setOriginCountry(legs.get(0).getOriginCountry());
            itinerary.setDestinationCountry(legs.get(legs.size() - 1).getDestinationCountry());
        }
        itineraryRepository.save(itinerary);
    }

    /**
     * Rebuild this itinerary's {@link HandlingCharge} rows from its legs and
     * return their total. One charge per interchange — the stationary work of
     * getting cargo off one leg and onto the next at the place they meet.
     *
     * <p>Charges are fully derived from {@link TransferPolicy}, never
     * client-supplied, so they can't drift from the rates the route planner
     * quoted. They're replaced wholesale rather than diffed: an itinerary edit
     * can change any leg's mode, endpoints or order.
     */
    private BigDecimal recalcHandling(Itinerary itinerary, List<Shipment> legs) {
        List<HandlingCharge> existing =
                handlingChargeRepository.findByItineraryOrderByAfterLegSequenceAsc(itinerary);
        if (!existing.isEmpty()) handlingChargeRepository.deleteAllInBatch(existing);
        // Keep the inverse side in sync so a just-built itinerary reports its
        // charges when mapped in this same session — the @OneToMany is
        // mappedBy/inverse and isn't auto-populated (same reason legs are
        // synced by hand in TmsTreeService).
        if (itinerary.getHandlingCharges() != null) itinerary.getHandlingCharges().clear();

        String currency = itinerary.getCurrency() != null ? itinerary.getCurrency() : "EUR";
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i + 1 < legs.size(); i++) {
            Shipment from = legs.get(i);
            Shipment to = legs.get(i + 1);
            if (!needsInterchange(from.getMode(), to.getMode())) continue;
            Location at = interchangeLocation(from, to);
            if (at == null) continue; // consecutive legs don't actually meet
            // Gate on the terminal's capability, exactly as the routing graph
            // does: no profile there means no interchange is possible, so
            // nothing is charged (a plain ADDRESS handles no modes at all).
            Set<Shipment.Mode> handled = transferPolicy.modesHandledAt(at.getLocationType());
            if (!handled.contains(from.getMode()) || !handled.contains(to.getMode())) continue;

            BigDecimal amount = scale(BigDecimal.valueOf(
                    transferPolicy.transferCost(from.getMode(), to.getMode())));
            HandlingCharge charge = new HandlingCharge();
            charge.setItinerary(itinerary);
            charge.setLocation(at);
            charge.setFromMode(from.getMode());
            charge.setToMode(to.getMode());
            charge.setAfterLegSequence(from.getLegSequence());
            charge.setAmount(amount);
            charge.setCurrency(currency);
            charge = handlingChargeRepository.save(charge);
            if (itinerary.getHandlingCharges() != null) itinerary.getHandlingCharges().add(charge);
            total = total.add(amount);
        }
        return total;
    }

    /**
     * Does moving from {@code from} to {@code to} need terminal handling?
     * Mirrors {@code RoutePlanner.relax}'s transfer condition so a quote and a
     * bill agree: a mode change, or boarding a scheduled service. ROAD is the
     * one unscheduled mode in this model (road legs are virtual and
     * untimetabled), so "the next leg is scheduled" is exactly "its mode isn't
     * ROAD". Road→road is one truck driving on, and free.
     */
    private static boolean needsInterchange(Shipment.Mode from, Shipment.Mode to) {
        if (from == null || to == null) return false;
        return from != to || to != Shipment.Mode.ROAD;
    }

    /** The location two consecutive legs share — the first's DELIVERY and the
     *  second's PICKUP. Null when either is missing or they differ, which means
     *  the itinerary has a gap rather than an interchange, and nothing is
     *  charged for work that isn't happening. */
    private Location interchangeLocation(Shipment from, Shipment to) {
        Location dropped = stopLocation(from, Stop.StopType.DELIVERY);
        Location collected = stopLocation(to, Stop.StopType.PICKUP);
        if (dropped == null || collected == null) return null;
        if (dropped.getId() == null || !dropped.getId().equals(collected.getId())) return null;
        return dropped;
    }

    private Location stopLocation(Shipment leg, Stop.StopType type) {
        return stopRepository.findByShipmentOrderBySequenceAsc(leg).stream()
                .filter(s -> s.getType() == type)
                .map(Stop::getLocation)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Resolve the chargeable quantity for a unit from the leg's metrics; null
     *  when the leg lacks that metric (caller then uses the hourly fallback). */
    private BigDecimal chargeableQuantity(ChargeUnit unit, Shipment s) {
        switch (unit) {
            case PER_KM:
                return s.getDistanceKm();
            case PER_CONTAINER:
                return s.getContainerCount() != null ? BigDecimal.valueOf(s.getContainerCount()) : null;
            case PER_PIECE:
                return s.getPieceCount() != null ? BigDecimal.valueOf(s.getPieceCount()) : null;
            case PER_CHARGEABLE_KG:
                return chargeableKg(s);
            case FLAT:
                return BigDecimal.ONE;
            case PER_HOUR:
            default:
                return null;
        }
    }

    /** Air chargeable weight = max(actual kg, volumetric kg), volumetric =
     *  volume_m³ × 1,000,000 / 6000 (IATA). Null when neither is known. */
    private BigDecimal chargeableKg(Shipment s) {
        BigDecimal actual = s.getWeightKg();
        BigDecimal volumetric = s.getVolumeM3() != null
                ? s.getVolumeM3().multiply(new BigDecimal("1000000"))
                        .divide(PricingPolicy.AIR_VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP)
                : null;
        if (actual == null) return volumetric;
        if (volumetric == null) return actual;
        return actual.max(volumetric);
    }

    private BigDecimal hourlyCostOf(Load load) {
        BigDecimal rate = load.getRatePerHour() != null ? load.getRatePerHour() : BigDecimal.ZERO;
        return rate.multiply(hoursOf(load));
    }

    private BigDecimal hoursOf(Load load) {
        double hours = load.getEstimatedDurationHours() != null ? load.getEstimatedDurationHours() : 0.0;
        return BigDecimal.valueOf(hours);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
