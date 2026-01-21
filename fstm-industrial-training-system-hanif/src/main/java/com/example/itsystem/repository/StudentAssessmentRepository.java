package com.example.itsystem.repository;

import com.example.itsystem.model.StudentAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentAssessmentRepository extends JpaRepository<StudentAssessment, Long> {

    Optional<StudentAssessment> findByStudentUserIdAndSession(Long studentUserId, String session);

    // ✅ 让 StudentController 用的“取最新一条记录”能编译通过
    Optional<StudentAssessment> findTopByStudentUserIdOrderByIdDesc(Long studentUserId);


}