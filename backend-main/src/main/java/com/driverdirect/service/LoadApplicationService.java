package com.driverdirect.service;

import com.driverdirect.dto.LoadApplicationRequest;
import com.driverdirect.dto.LoadApplicationResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Load;

import java.util.List;
import java.util.Map;

public interface LoadApplicationService {

    /** Why a carrier can or cannot apply for a load. OK means they may apply.
     *  applyForLoad enforces exactly this; the admin UI previews it. */
    enum Eligibility { OK, LOAD_NOT_OPEN, ALREADY_APPLIED, MODE_UNSUPPORTED, LICENCE, AVAILABILITY, CABOTAGE }

    /** Non-mutating preview of whether {@code carrier} may apply for {@code load}. */
    Eligibility checkEligibility(Carrier carrier, Load load);

    /** Batched eligibility for many carriers against one load, keyed by carrier id.
     *  Resolves applications/availability/cabotage in a constant number of
     *  queries (not per carrier). */
    Map<Long, Eligibility> checkEligibilityForCarriers(Load load, List<Carrier> carriers);

    LoadApplicationResponse applyForLoad(Carrier carrier, Long loadId, LoadApplicationRequest request);

    List<LoadApplicationResponse> getApplicationsByCarrier(Carrier carrier);

    List<LoadApplicationResponse> getApplicationsForLoad(Long loadId, Shipper shipper);

    LoadApplicationResponse acceptApplication(Long applicationId, Shipper shipper);

    LoadApplicationResponse rejectApplication(Long applicationId, Shipper shipper);

    LoadApplicationResponse withdrawApplication(Long applicationId, Carrier carrier);
}
