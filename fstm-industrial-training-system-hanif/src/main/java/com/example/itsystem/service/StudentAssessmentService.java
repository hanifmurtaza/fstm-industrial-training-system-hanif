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
    // Visiting Lecturer (60%) - legacy
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
    // Visiting Lecturer (40%) - OFFICIAL
    // =========================
    /**
     * OFFICIAL FLOW (2026):
     * Visiting Lecturer submits a VisitEvaluation; we sync its totalScore40 into StudentAssessment.
     *
     * Note: we store VL total (/40) into vlFinalReport40 to avoid DB schema changes.
     * Other VL buckets are kept at 0.
     */
    @Transactional
    public StudentAssessment saveVisitingLecturerScore40(
            Long studentId,
            String session,
            Long lecturerId,
            BigDecimal totalScore40,
            boolean submit
    ) {
        StudentAssessment sa = getOrCreate(studentId, session, lecturerId);

        // keep legacy buckets empty to avoid confusion
        sa.setVlEvaluation10(BigDecimal.ZERO);
        sa.setVlAttendance5(BigDecimal.ZERO);
        sa.setVlLogbook5(BigDecimal.ZERO);

        // store VL total /40 here
        sa.setVlFinalReport40(clamp(nz(totalScore40), BigDecimal.ZERO, new BigDecimal("40")));

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);

        recomputeGrade(sa);
        return repo.save(sa);
    }

    // =========================
    // Industry Supervisor (40%) - OFFICIAL
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

        // clear legacy buckets (optional but avoids confusion)
        sa.setIsSkills20(BigDecimal.ZERO);
        sa.setIsCommunication10(BigDecimal.ZERO);
        sa.setIsTeamwork10(BigDecimal.ZERO);

        sa.setStatus(submit ? StudentAssessment.Status.SUBMITTED : StudentAssessment.Status.DRAFT);
        recomputeGrade(sa);
        return repo.save(sa);
    }

    // =========================
    // Industry Supervisor - legacy
    // =========================
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
    // Admin / Coordinator marks (OFFICIAL 2026)
    // =========================
    @Transactional
    public StudentAssessment saveAdminFinalReportMarks(
            Long studentId,
            String session,
            BigDecimal written5,
            BigDecimal video5
    ) {
        StudentAssessment sa = repo.findByStudentUserIdAndSession(studentId, session)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(session);
                    x.setStatus(StudentAssessment.Status.DRAFT);
                    return repo.save(x);
                });

        sa.setAdminReportWritten5(clamp(nz(written5), BigDecimal.ZERO, new BigDecimal("5")));
        sa.setAdminReportVideo5(clamp(nz(video5), BigDecimal.ZERO, new BigDecimal("5")));

        recomputeGrade(sa);
        return repo.save(sa);
    }

    @Transactional
    public StudentAssessment saveAdminLogbookMark(
            Long studentId,
            String session,
            BigDecimal logbook10
    ) {
        StudentAssessment sa = repo.findByStudentUserIdAndSession(studentId, session)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(session);
                    x.setStatus(StudentAssessment.Status.DRAFT);
                    return repo.save(x);
                });

        sa.setAdminLogbook10(clamp(nz(logbook10), BigDecimal.ZERO, new BigDecimal("10")));

        recomputeGrade(sa);
        return repo.save(sa);
    }

    // =========================
    // Grade calculation (OFFICIAL 2026)
    // VL (40) + Industry (40) + Admin Final Report (10) + Admin Logbook (10) = 100
    // =========================
    private void recomputeGrade(StudentAssessment sa) {

        // -------- Visiting Lecturer (40) --------
        BigDecimal legacyVl60 = nz(sa.getVlEvaluation10())
                .add(nz(sa.getVlAttendance5()))
                .add(nz(sa.getVlLogbook5()))
                .add(nz(sa.getVlFinalReport40())); // legacy max 60

        boolean hasLegacyBuckets =
                nz(sa.getVlEvaluation10()).compareTo(BigDecimal.ZERO) > 0
                        || nz(sa.getVlAttendance5()).compareTo(BigDecimal.ZERO) > 0
                        || nz(sa.getVlLogbook5()).compareTo(BigDecimal.ZERO) > 0;

        BigDecimal vl40;
        if (hasLegacyBuckets) {
            // scale 60 -> 40
            vl40 = legacyVl60
                    .multiply(BigDecimal.valueOf(40))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        } else {
            // new rows: vlFinalReport40 is actually /40
            vl40 = nz(sa.getVlFinalReport40()).setScale(2, RoundingMode.HALF_UP);
        }

        // -------- Industry Supervisor (40) --------
        BigDecimal ind40 = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10()));
        if (ind40.compareTo(BigDecimal.ZERO) == 0) {
            ind40 = nz(sa.getIsSkills20())
                    .add(nz(sa.getIsCommunication10()))
                    .add(nz(sa.getIsTeamwork10()));
        }

        // -------- Admin / Coordinator --------
        BigDecimal report10 = nz(sa.getAdminReportWritten5()).add(nz(sa.getAdminReportVideo5())); // /10
        BigDecimal logbook10 = nz(sa.getAdminLogbook10()); // /10

        BigDecimal total = vl40.add(ind40).add(report10).add(logbook10); // /100

        double totalDouble = total
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        sa.setTotal100(total);
        sa.setGrade(UpmGradeUtil.gradeFromTotal(totalDouble));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal clamp(BigDecimal v, BigDecimal min, BigDecimal max) {
        if (v.compareTo(min) < 0) return min;
        if (v.compareTo(max) > 0) return max;
        return v;
    }
}
