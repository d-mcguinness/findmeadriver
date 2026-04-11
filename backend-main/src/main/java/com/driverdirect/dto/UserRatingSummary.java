package com.driverdirect.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserRatingSummary {
    private Double averageRating;
    private Long totalRatings;
    private List<RatingResponse> recentRatings;
}
