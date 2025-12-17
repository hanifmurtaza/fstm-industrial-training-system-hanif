package com.example.itsystem.service;

import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.StudentAssessmentRepository;
import com.example.itsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminEvaluationService {

    private final StudentAssessmentRepository assessmentRepo;
    private final UserRepository userRepo;

    public AdminEvaluationService(StudentAssessmentRepository assessmentRepo, UserRepository userRepo) {
        this.assessmentRepo = assessmentRepo;
        this.userRepo = userRepo;
    }

    public List<EvaluationExportService.EvaluationRow> buildRows(String search, String sessionFilter) {

        List<StudentAssessment> list = assessmentRepo.findAll();

        // ---- Session filter ----
        if (sessionFilter != null && !sessionFilter.isBlank() && !"ALL".equalsIgnoreCase(sessionFilter)) {
            String s = sessionFilter.trim();
            list = list.stream()
                    .filter(a -> a.getSession() != null && a.getSession().trim().equalsIgnoreCase(s))
                    .collect(Collectors.toList());
        }

        // ---- preload students ----
        Set<Long> studentIds = list.stream()
                .map(StudentAssessment::getStudentUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> studentById = new HashMap<>();
        if (!studentIds.isEmpty()) {
            userRepo.findAllById(studentIds).forEach(u -> studentById.put(u.getId(), u));
        }

        // ---- Search filter (name or matric) ----
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            list = list.stream().filter(a -> {
                User u = studentById.get(a.getStudentUserId());
                String name = (u != null && u.getName() != null) ? u.getName().toLowerCase() : "";
                String matric = (u != null && u.getStudentId() != null) ? u.getStudentId().toLowerCase() : "";
                return name.contains(q) || matric.contains(q);
            }).collect(Collectors.toList());
        }

        // Optional: stable ordering (latest first)
        list.sort(Comparator.comparing(StudentAssessment::getId, Comparator.nullsLast(Long::compareTo)).reversed());

        List<EvaluationExportService.EvaluationRow> rows = new ArrayList<>();
        int no = 1;

        for (StudentAssessment a : list) {
            User student = studentById.get(a.getStudentUserId());

            BigDecimal vlTotal = sum(
                    a.getVlEvaluation10(),
                    a.getVlAttendance5(),
                    a.getVlLogbook5(),
                    a.getVlFinalReport40()
            ); // /60

            BigDecimal indTotal = sum(
                    a.getIsSkills20(),
                    a.getIsCommunication10(),
                    a.getIsTeamwork10()
            ); // /40

            BigDecimal overall = (vlTotal == null ? BigDecimal.ZERO : vlTotal)
                    .add(indTotal == null ? BigDecimal.ZERO : indTotal); // /100

            // match your UI "No data" feeling:
            boolean vlNoData = isZero(vlTotal);
            boolean indNoData = isZero(indTotal);

            EvaluationExportService.EvaluationRow r = new EvaluationExportService.EvaluationRow();
            r.no = no++;

            r.studentName = (student != null && student.getName() != null && !student.getName().isBlank())
                    ? student.getName()
                    : (student != null ? student.getUsername() : "");

            r.matric = (student != null && student.getStudentId() != null) ? student.getStudentId() : "";
            r.session = (a.getSession() != null) ? a.getSession() : "";

            r.vlText = vlNoData ? "No data" : fmt(vlTotal) + " / 60";
            r.industryText = indNoData ? "No data" : fmt(indTotal) + " / 40";

            // if both missing, show "-"
            r.total = (vlNoData && indNoData) ? "-" : fmt(overall);

            r.grade = (a.getGrade() == null || a.getGrade().isBlank()) ? "-" : a.getGrade();
            r.status = (a.getStatus() == null) ? "" : a.getStatus().name();

            rows.add(r);
        }

        return rows;
    }

    private BigDecimal sum(BigDecimal... vals) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            if (v != null) t = t.add(v);
        }
        return t;
    }

    private boolean isZero(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) == 0;
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
