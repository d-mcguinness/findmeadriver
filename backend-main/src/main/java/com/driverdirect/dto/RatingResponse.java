package com.driverdirect.dto;

import com.driverdirect.model.Rating;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RatingResponse {
    private Long id;
    private Long loadId;
    private String loadTitle;
    private String reviewerName;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;

    public static RatingResponse from(Rating rating) {
        RatingResponse r = new RatingResponse();
        r.setId(rating.getId());
        r.setLoadId(rating.getLoad().getId());
        r.setLoadTitle(rating.getLoad().getTitle());
        r.setReviewerName(rating.getReviewer().getFirstName() + " " + rating.getReviewer().getLastName());
        r.setScore(rating.getScore());
        r.setComment(rating.getComment());
        r.setCreatedAt(rating.getCreatedAt());
        return r;
    }
}
