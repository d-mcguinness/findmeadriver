package com.driverdirect.repository;

import com.driverdirect.model.Job;
import com.driverdirect.model.Rating;
import com.driverdirect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByRevieweeOrderByCreatedAtDesc(User reviewee);

    boolean existsByJobAndReviewer(Job job, User reviewer);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.reviewee.id = :id")
    Optional<Double> findAverageScoreByRevieweeId(@Param("id") Long revieweeId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.reviewee.id = :id")
    long countByRevieweeId(@Param("id") Long revieweeId);
}
