package com.example.itsystem.service;

import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.repository.StudentAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StudentAssessmentService {

    private final StudentAssessmentRepository repo;

    // 手写构造器（不使用 Lombok）
    public StudentAssessmentService(StudentAssessmentRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public StudentAssessment getOrCreate(Long studentId, String session, Long lecturerId) {
        return repo.findByStudentUserIdAndSession(studentId, session)
                .orElseGet(() -> {
                    StudentAssessment sa = new StudentAssessment();
                    sa.setStudentUserId(studentId);
                    sa.setSession(session);
                    sa.setVisitingLecturerId(lecturerId);
                    return repo.save(sa);
                });
    }

    @Transactional
    public StudentAssessment saveVisitingLecturerScores(
            Long studentId, String session, Long lecturerId,
            BigDecimal eval10, BigDecimal attend5, BigDecimal logbook5, BigDecimal report40,
            boolean submit) {

        StudentAssessment sa = getOrCreate(studentId, session, lecturerId);
        if (sa.getVisitingLecturerId() == null) {
            sa.setVisitingLecturerId(lecturerId);
        }

        sa.setVlEvaluation10(eval10);
        sa.setVlAttendance5(attend5);
        sa.setVlLogbook5(logbook5);
        sa.setVlFinalReport40(report40);

        if (submit) {
            sa.setStatus(StudentAssessment.Status.SUBMITTED);
        }
        return repo.save(sa);
    }
}
