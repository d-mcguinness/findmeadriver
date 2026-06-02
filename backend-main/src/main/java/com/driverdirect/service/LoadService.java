package com.driverdirect.service;

import com.driverdirect.dto.CreateIntermodalLoadRequest;
import com.driverdirect.dto.CreateLoadRequest;
import com.driverdirect.dto.ItineraryResponse;
import com.driverdirect.dto.LoadResponse;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.LoadStatus;

import java.util.List;

public interface LoadService {

    LoadResponse createLoad(Shipper shipper, CreateLoadRequest request);

    List<LoadResponse> getLoadsByShipper(Shipper shipper);

    LoadResponse getLoadById(Long id);

    List<LoadResponse> getMatchingLoads(Carrier carrier);

    LoadResponse updateLoadStatus(Long loadId, Shipper shipper, LoadStatus status);

    // ---- Intermodal (M2b) ----

    ItineraryResponse createIntermodalLoad(Shipper shipper, CreateIntermodalLoadRequest request);

    List<ItineraryResponse> getItinerariesByShipper(Shipper shipper);

    ItineraryResponse getItineraryById(Long id, Shipper shipper);
}
