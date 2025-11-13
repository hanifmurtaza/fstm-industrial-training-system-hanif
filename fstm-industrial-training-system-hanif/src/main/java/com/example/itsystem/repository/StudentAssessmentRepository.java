package com.example.itsystem.repository;

import com.example.itsystem.model.StudentAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentAssessmentRepository extends JpaRepository<StudentAssessment, Long> {

    Optional<StudentAssessment> findByStudentUserIdAndSession(Long studentUserId, String session);

    Optional<StudentAssessment> findTopByStudentUserIdOrderByIdDesc(Long studentUserId);

    List<StudentAssessment> findAllByVisitingLecturerIdAndSession(Long lecturerId, String session);
}
