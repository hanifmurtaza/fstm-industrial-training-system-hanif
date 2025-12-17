package com.example.itsystem.service;

import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.repository.StudentAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StudentAssessmentService {

    private final StudentAssessmentRepository repo;

    public StudentAssessmentService(StudentAssessmentRepository repo) {
        this.repo = repo;
    }

    /**
     * ✅ getOrCreate 必须按 (studentId, session, lecturerId) 找
     * 否则同一个 student+session 会读到别的 lecturer 的记录，导致“看起来没保存”
     */
    @Transactional
    public StudentAssessment getOrCreate(Long studentId, String session, Long lecturerId) {
        return repo.findByStudentUserIdAndSessionAndVisitingLecturerId(studentId, session, lecturerId)
                .orElseGet(() -> {
                    StudentAssessment sa = new StudentAssessment();
                    sa.setStudentUserId(studentId);
                    sa.setSession(session);
                    sa.setVisitingLecturerId(lecturerId);
                    // 建议默认状态
                    if (sa.getStatus() == null) {
                        sa.setStatus(StudentAssessment.Status.DRAFT);
                    }
                    return repo.save(sa);
                });
    }

    @Transactional
    public StudentAssessment saveVisitingLecturerScores(
            Long studentId, String session, Long lecturerId,
            BigDecimal eval10, BigDecimal attend5, BigDecimal logbook5, BigDecimal report40,
            boolean submit) {

        StudentAssessment sa = getOrCreate(studentId, session, lecturerId);

        // 保底：确保 lecturerId 写进去
        if (sa.getVisitingLecturerId() == null) {
            sa.setVisitingLecturerId(lecturerId);
        }

        sa.setVlEvaluation10(eval10);
        sa.setVlAttendance5(attend5);
        sa.setVlLogbook5(logbook5);
        sa.setVlFinalReport40(report40);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);

        return repo.save(sa);
    }
}