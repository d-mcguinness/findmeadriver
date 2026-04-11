package com.driverdirect.service;

import com.driverdirect.dto.CreateRatingRequest;
import com.driverdirect.dto.RatingResponse;
import com.driverdirect.dto.UserRatingSummary;
import com.driverdirect.model.*;
import com.driverdirect.repository.JobRepository;
import com.driverdirect.repository.RatingRepository;
import com.driverdirect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RatingResponse createRating(User reviewer, Long jobId, CreateRatingRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only rate completed jobs");
        }

        if (request.getScore() == null || request.getScore() < 1 || request.getScore() > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        if (ratingRepository.existsByJobAndReviewer(job, reviewer)) {
            throw new IllegalArgumentException("You have already rated this job");
        }

        // Determine who is being rated (the other party)
        User reviewee;
        if (reviewer.getId().equals(job.getEmployer().getId())) {
            // Employer is rating the driver
            if (job.getAssignedDriver() == null) {
                throw new IllegalArgumentException("No driver was assigned to this job");
            }
            reviewee = job.getAssignedDriver();
        } else if (job.getAssignedDriver() != null && reviewer.getId().equals(job.getAssignedDriver().getId())) {
            // Driver is rating the employer
            reviewee = job.getEmployer();
        } else {
            throw new IllegalArgumentException("You are not associated with this job");
        }

        Rating rating = new Rating();
        rating.setJob(job);
        rating.setReviewer(reviewer);
        rating.setReviewee(reviewee);
        rating.setScore(request.getScore());
        rating.setComment(request.getComment());

        rating = ratingRepository.save(rating);
        return RatingResponse.from(rating);
    }

    @Override
    public UserRatingSummary getRatingSummary(Long userId) {
        UserRatingSummary summary = new UserRatingSummary();
        summary.setAverageRating(ratingRepository.findAverageScoreByRevieweeId(userId).orElse(null));
        summary.setTotalRatings(ratingRepository.countByRevieweeId(userId));
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            summary.setRecentRatings(
                    ratingRepository.findByRevieweeOrderByCreatedAtDesc(user).stream()
                    .limit(10)
                    .map(RatingResponse::from)
                    .collect(Collectors.toList())
            );
        } else {
            summary.setRecentRatings(List.of());
        }
        return summary;
    }

    @Override
    public boolean hasRated(User reviewer, Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return false;
        return ratingRepository.existsByJobAndReviewer(job, reviewer);
    }

    @Override
    public Double getAverageRating(Long userId) {
        return ratingRepository.findAverageScoreByRevieweeId(userId).orElse(null);
    }

    @Override
    public Long getRatingCount(Long userId) {
        return ratingRepository.countByRevieweeId(userId);
    }
}
