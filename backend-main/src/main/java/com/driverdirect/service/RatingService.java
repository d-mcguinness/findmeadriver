package com.driverdirect.service;

import com.driverdirect.dto.CreateRatingRequest;
import com.driverdirect.dto.RatingResponse;
import com.driverdirect.dto.UserRatingSummary;
import com.driverdirect.model.User;

public interface RatingService {

    RatingResponse createRating(User reviewer, Long loadId, CreateRatingRequest request);

    UserRatingSummary getRatingSummary(Long userId);

    boolean hasRated(User reviewer, Long loadId);

    Double getAverageRating(Long userId);

    Long getRatingCount(Long userId);
}
