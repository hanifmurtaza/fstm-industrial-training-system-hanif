package com.example.itsystem.repository;

import com.example.itsystem.model.SupervisorEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;


public interface SupervisorEvaluationRepository extends JpaRepository<SupervisorEvaluation, Long> {
    Optional<SupervisorEvaluation> findByPlacementId(Long placementId);
    List<SupervisorEvaluation> findByPlacementIdIn(List<Long> placementIds);

    boolean existsByPlacementId(Long placementId);
}
