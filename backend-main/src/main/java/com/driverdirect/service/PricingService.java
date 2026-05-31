package com.driverdirect.service;

import com.driverdirect.model.ChargeUnit;
import com.driverdirect.model.Itinerary;
import com.driverdirect.model.Job;
import com.driverdirect.model.Shipment;
import com.driverdirect.repository.ItineraryRepository;
import com.driverdirect.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes the money on a Job's Shipment leg (M1b):
 * <pre>
 *   carrierCost   = ratePerHour × estimatedDurationHours   (what the carrier earns)
 *   commission    = carrierCost × commissionPercent(mode)  (the platform's mode-dependent cut)
 *   employerTotal = carrierCost + commission               (what the employer pays)
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

    /**
     * Prices the leg of {@code job} and persists the result onto its Shipment.
     * No-op for a Job not yet linked to a Shipment.
     */
    @Transactional
    public void priceJob(Job job) {
        if (job == null || job.getShipment() == null) return;
        // Re-load the leg in THIS transaction. The passed Job may be detached
        // (e.g. the non-transactional seed path), where its lazy @ManyToOne
        // shipment is an uninitialised proxy on a closed session. getId() on a
        // proxy is safe (no init); findById then gives us a managed entity.
        Shipment shipment = shipmentRepository.findById(job.getShipment().getId()).orElse(null);
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
            carrierCost = scale(hourlyCostOf(job));
            shipment.setChargeUnit(ChargeUnit.PER_HOUR);
            shipment.setChargeableQuantity(hoursOf(job));
        }

        BigDecimal pct = pricingPolicy.commissionPercentFor(mode);
        BigDecimal commission = scale(carrierCost.multiply(pct)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        shipment.setTotalRate(carrierCost);
        shipment.setCommissionPercent(pct);
        shipment.setCommissionAmount(commission);
        shipment.setEmployerTotal(carrierCost.add(commission));
        shipmentRepository.save(shipment);
    }

    /**
     * Roll the already-priced legs of an intermodal Itinerary up into its
     * carrier / commission / grand totals (M2). Each leg ({@link Shipment}) must
     * have been priced via {@link #priceJob} first. Mixed currencies across legs
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
            grand = grand.add(nz(leg.getEmployerTotal()));
        }

        itinerary.setCarrierCostTotal(scale(carrier));
        itinerary.setCommissionTotal(scale(commission));
        itinerary.setGrandTotal(scale(grand));
        if (!legs.isEmpty()) {
            itinerary.setOriginCountry(legs.get(0).getOriginCountry());
            itinerary.setDestinationCountry(legs.get(legs.size() - 1).getDestinationCountry());
        }
        itineraryRepository.save(itinerary);
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

    private BigDecimal hourlyCostOf(Job job) {
        BigDecimal rate = job.getRatePerHour() != null ? job.getRatePerHour() : BigDecimal.ZERO;
        return rate.multiply(hoursOf(job));
    }

    private BigDecimal hoursOf(Job job) {
        double hours = job.getEstimatedDurationHours() != null ? job.getEstimatedDurationHours() : 0.0;
        return BigDecimal.valueOf(hours);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
