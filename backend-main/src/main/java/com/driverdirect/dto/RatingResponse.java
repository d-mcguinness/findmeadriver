package com.driverdirect.dto;

import com.driverdirect.model.Rating;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RatingResponse {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private String reviewerName;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;

    public static RatingResponse from(Rating rating) {
        RatingResponse r = new RatingResponse();
        r.setId(rating.getId());
        r.setJobId(rating.getJob().getId());
        r.setJobTitle(rating.getJob().getTitle());
        r.setReviewerName(rating.getReviewer().getFirstName() + " " + rating.getReviewer().getLastName());
        r.setScore(rating.getScore());
        r.setComment(rating.getComment());
        r.setCreatedAt(rating.getCreatedAt());
        return r;
    }
}
