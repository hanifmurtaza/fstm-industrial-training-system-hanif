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

    @Transactional
    public StudentAssessment getOrCreate(Long studentId, String session, Long lecturerId) {
        return repo.findByStudentUserIdAndSessionAndVisitingLecturerId(studentId, session, lecturerId)
                .orElseGet(() -> {
                    StudentAssessment sa = new StudentAssessment();
                    sa.setStudentUserId(studentId);
                    sa.setSession(session);
                    sa.setVisitingLecturerId(lecturerId);
                    if (sa.getStatus() == null) {
                        sa.setStatus(StudentAssessment.Status.DRAFT);
                    }
                    return repo.save(sa);
                });
    }

    @Transactional
    public StudentAssessment saveVisitingLecturerScores(
            Long studentId,
            String session,
            Long lecturerId,
            BigDecimal eval10,
            BigDecimal attend5,
            BigDecimal logbook5,
            BigDecimal report40,
            boolean submit
    ) {
        StudentAssessment sa = getOrCreate(studentId, session, lecturerId);

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

    /**
     * Industry Supervisor evaluation (40%) -> Student Dashboard
     * Skills(20), Communication(10), Teamwork(10)
     */
    @Transactional
    public StudentAssessment saveIndustrySupervisorScores(
            Long studentId,
            String session,
            Long supervisorId,
            BigDecimal skills20,
            BigDecimal communication10,
            BigDecimal teamwork10,
            boolean submit
    ) {
        StudentAssessment sa = repo.findByStudentUserIdAndSession(studentId, session)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(session);
                    if (x.getStatus() == null) {
                        x.setStatus(StudentAssessment.Status.DRAFT);
                    }
                    return repo.save(x);
                });

        if (sa.getIndustrySupervisorId() == null) {
            sa.setIndustrySupervisorId(supervisorId);
        }

        sa.setIsSkills20(skills20);
        sa.setIsCommunication10(communication10);
        sa.setIsTeamwork10(teamwork10);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);

        return repo.save(sa);
    }
}
