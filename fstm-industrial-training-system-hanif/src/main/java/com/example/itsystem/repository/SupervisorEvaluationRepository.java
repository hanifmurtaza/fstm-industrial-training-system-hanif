package com.example.itsystem.repository;

import com.example.itsystem.model.SupervisorEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupervisorEvaluationRepository extends JpaRepository<SupervisorEvaluation, Long> {
    Optional<SupervisorEvaluation> findByPlacementId(Long placementId);
    boolean existsByPlacementId(Long placementId);
}
