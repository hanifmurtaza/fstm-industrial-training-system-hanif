package com.example.itsystem.repository;

import com.example.itsystem.model.Evaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Page<Evaluation> findByStudent_NameContainingOrStudent_StudentIdContaining(String name, String studentId, Pageable pageable);
}
