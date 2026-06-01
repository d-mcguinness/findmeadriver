package com.driverdirect.service;

import com.driverdirect.dto.CreateRatingRequest;
import com.driverdirect.dto.RatingResponse;
import com.driverdirect.dto.UserRatingSummary;
import com.driverdirect.model.*;
import com.driverdirect.repository.LoadRepository;
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
    private final LoadRepository loadRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RatingResponse createRating(User reviewer, Long loadId, CreateRatingRequest request) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new IllegalArgumentException("Load not found"));

        if (load.getStatus() != LoadStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only rate completed loads");
        }

        if (request.getScore() == null || request.getScore() < 1 || request.getScore() > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        if (ratingRepository.existsByLoadAndReviewer(load, reviewer)) {
            throw new IllegalArgumentException("You have already rated this load");
        }

        // Determine who is being rated (the other party)
        User reviewee;
        if (reviewer.getId().equals(load.getShipper().getId())) {
            // Shipper is rating the carrier
            if (load.getAssignedCarrier() == null) {
                throw new IllegalArgumentException("No carrier was assigned to this load");
            }
            reviewee = load.getAssignedCarrier();
        } else if (load.getAssignedCarrier() != null && reviewer.getId().equals(load.getAssignedCarrier().getId())) {
            // Carrier is rating the shipper
            reviewee = load.getShipper();
        } else {
            throw new IllegalArgumentException("You are not associated with this load");
        }

        Rating rating = new Rating();
        rating.setLoad(load);
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
    public boolean hasRated(User reviewer, Long loadId) {
        Load load = loadRepository.findById(loadId).orElse(null);
        if (load == null) return false;
        return ratingRepository.existsByLoadAndReviewer(load, reviewer);
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
