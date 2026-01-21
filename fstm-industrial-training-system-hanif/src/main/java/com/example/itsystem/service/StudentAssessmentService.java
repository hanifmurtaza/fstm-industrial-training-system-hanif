package com.example.itsystem.service;

import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.repository.StudentAssessmentRepository;
import com.example.itsystem.util.UpmGradeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StudentAssessmentService {

    private final StudentAssessmentRepository repo;

    public StudentAssessmentService(StudentAssessmentRepository repo) {
        this.repo = repo;
    }

    /**
     * ✅ Must be (studentId, session, visitingLecturerId)
     * Otherwise different lecturers overwrite each other
     */
    @Transactional
    public StudentAssessment getOrCreate(Long studentId, String session, Long lecturerId) {
        String s = (session == null) ? null : session.trim();

        StudentAssessment sa = repo.findByStudentUserIdAndSession(studentId, s)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(s);
                    x.setStatus(StudentAssessment.Status.DRAFT);
                    return repo.save(x);
                });

        // bind VL id if not set
        if (lecturerId != null && sa.getVisitingLecturerId() == null) {
            sa.setVisitingLecturerId(lecturerId);
        }
        return sa;
    }



    // =========================
    // Visiting Lecturer (60%)
    // =========================
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

        sa.setVlEvaluation10(eval10);
        sa.setVlAttendance5(attend5);
        sa.setVlLogbook5(logbook5);
        sa.setVlFinalReport40(report40);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);

        recomputeGrade(sa);
        return repo.save(sa);
    }

    // =========================
    // Industry Supervisor (40%)
    // =========================
    /**
     * ✅ OFFICIAL RUBRIC (2026): Section B (30) + Section C (10) = 40
     */
    @Transactional
    public StudentAssessment saveIndustrySupervisorOfficialScores(
            Long studentId,
            String session,
            Long supervisorId,
            BigDecimal attributes30,
            BigDecimal overall10,
            boolean submit
    ) {
        StudentAssessment sa = repo.findByStudentUserIdAndSession(studentId, session)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(session);
                    x.setStatus(StudentAssessment.Status.DRAFT);
                    return repo.save(x);
                });

        if (sa.getIndustrySupervisorId() == null) {
            sa.setIndustrySupervisorId(supervisorId);
        }

        sa.setIsAttributes30(attributes30);
        sa.setIsOverall10(overall10);

        // Keep legacy buckets cleared to avoid confusion in UI (optional)
        sa.setIsSkills20(BigDecimal.ZERO);
        sa.setIsCommunication10(BigDecimal.ZERO);
        sa.setIsTeamwork10(BigDecimal.ZERO);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);
        recomputeGrade(sa);
        return repo.save(sa);
    }

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
                    x.setStatus(StudentAssessment.Status.DRAFT);
                    return repo.save(x);
                });

        if (sa.getIndustrySupervisorId() == null) {
            sa.setIndustrySupervisorId(supervisorId);
        }

        sa.setIsSkills20(skills20);
        sa.setIsCommunication10(communication10);
        sa.setIsTeamwork10(teamwork10);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);

        recomputeGrade(sa);
        return repo.save(sa);
    }

    // =========================
    // Grade calculation
    // =========================
    private void recomputeGrade(StudentAssessment sa) {
        BigDecimal vl =
                nz(sa.getVlEvaluation10())
                        .add(nz(sa.getVlAttendance5()))
                        .add(nz(sa.getVlLogbook5()))
                        .add(nz(sa.getVlFinalReport40())); // max 60

        // Industry Supervisor (40): prefer official rubric fields, fallback to legacy buckets
        BigDecimal ind = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10()));
        if (ind.compareTo(BigDecimal.ZERO) == 0) {
            ind = nz(sa.getIsSkills20())
                    .add(nz(sa.getIsCommunication10()))
                    .add(nz(sa.getIsTeamwork10()));
        }

        BigDecimal total = vl.add(ind); // max 100

        double totalDouble = total
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        sa.setTotal100(total);
        sa.setGrade(UpmGradeUtil.gradeFromTotal(totalDouble));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
