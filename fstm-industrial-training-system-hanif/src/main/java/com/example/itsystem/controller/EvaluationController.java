package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitEvaluation;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.VisitEvaluationRepository;
import com.example.itsystem.service.VisitScheduleService;
import com.example.itsystem.service.StudentAssessmentService;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/lecturer/evaluation")
public class EvaluationController {

    @Autowired
    private VisitEvaluationRepository evaluationRepository;

    @Autowired
    private VisitScheduleService visitScheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentAssessmentService studentAssessmentService;

    @GetMapping("/form/{visitId}")
    public String showEvaluationForm(@PathVariable Long visitId,
                                     @RequestParam(name = "session", required = false) String sessionFilter,
                                     HttpSession session,
                                     Model model) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) return "redirect:/login";

        VisitSchedule visit = visitScheduleService.findById(visitId);
        if (visit == null || !Objects.equals(visit.getLecturerId(), lecturer.getId())) {
            return "redirect:/lecturer/evaluation/list";
        }

        // existing evaluation (same visit) or new one
        VisitEvaluation evaluation = evaluationRepository.findFirstByVisitId(visitId).orElse(new VisitEvaluation());

        // bind ownership
        evaluation.setVisitId(visit.getId());
        evaluation.setStudentId(visit.getStudentId());
        evaluation.setLecturerId(lecturer.getId());

        // Part A convenience defaults (optional)
        if (evaluation.getVisitDate() == null && visit.getVisitDate() != null) {
            evaluation.setVisitDate(visit.getVisitDate());
        }
        if (evaluation.getVisitMode() == null && visit.getMode() != null) {
            evaluation.setVisitMode(visit.getMode());
        }

        model.addAttribute("evaluation", evaluation);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);
        return "lecturer/evaluation-form";
    }

    @PostMapping("/submit")
    public String submitEvaluation(@ModelAttribute VisitEvaluation evaluation,
                                   @RequestParam(name = "session", required = false) String sessionFilter,
                                   HttpSession session,
                                   RedirectAttributes ra) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) return "redirect:/login";

        // safety: force lecturerId from session
        evaluation.setLecturerId(lecturer.getId());
        evaluation.setUpdatedAt(LocalDateTime.now());

        // ✅ important: compute totalScore40 based on likert
        evaluation.recalcTotalScore40();

        evaluationRepository.save(evaluation);

        // ✅ Sync VL 40% into StudentAssessment (OFFICIAL 2026 grading)
        try {
            Long studentId = evaluation.getStudentId();
            if (studentId != null) {
                User student = userRepository.findById(studentId).orElse(null);
                String sessionStr = (student != null && student.getSession() != null && !student.getSession().isBlank())
                        ? student.getSession().trim()
                        : "DEFAULT";

                Integer score40 = evaluation.getTotalScore40() == null ? 0 : evaluation.getTotalScore40();
                studentAssessmentService.saveVisitingLecturerScore40(
                        studentId,
                        sessionStr,
                        lecturer.getId(),
                        java.math.BigDecimal.valueOf(score40),
                        true
                );
            }
        } catch (Exception ignore) {}


        ra.addFlashAttribute("successMessage", "Evaluation submitted successfully.");

        if (sessionFilter != null && !sessionFilter.isBlank()) {
            ra.addAttribute("session", sessionFilter);
        }
        return "redirect:/lecturer/evaluation/list";
    }

    /**
     * List page: students assigned to lecturer + latest visit mapping
     */
    @GetMapping("/list")
    public String showEvaluationList(@RequestParam(name = "session", required = false) String sessionFilter,
                                     Model model,
                                     HttpSession session) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) return "redirect:/login";

        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        List<User> students = (sessionFilter != null && !sessionFilter.isBlank())
                ? userRepository.findByLecturerAndSession(lecturer, sessionFilter)
                : userRepository.findByLecturer(lecturer);

        List<VisitSchedule> allVisits = visitScheduleService.findByLecturerId(lecturer.getId());

        Map<Long, VisitSchedule> latestVisitMap = new HashMap<>();
        for (VisitSchedule v : allVisits) {
            Long sid = v.getStudentId();
            VisitSchedule existing = latestVisitMap.get(sid);
            if (existing == null || v.getId() > existing.getId()) {
                latestVisitMap.put(sid, v);
            }
        }

        model.addAttribute("students", students);
        model.addAttribute("latestVisitMap", latestVisitMap);
        model.addAttribute("total", students.size());

        return "evaluation";
    }

    @GetMapping("/pdf/{visitId}")
    public void downloadEvaluationPdf(@PathVariable Long visitId,
                                      HttpServletResponse response) throws IOException {

        VisitEvaluation evaluation = evaluationRepository.findFirstByVisitId(visitId).orElse(null);
        if (evaluation == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Evaluation not found");
            return;
        }

        VisitSchedule schedule = visitScheduleService.findById(visitId);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=evaluation_" + visitId + ".pdf");

        try (OutputStream os = response.getOutputStream()) {
            PdfWriter writer = new PdfWriter(os);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Student Evaluation Form").setBold().setFontSize(16));

            document.add(new Paragraph("Student ID: " + evaluation.getStudentId()));
            document.add(new Paragraph("Visit Date: " +
                    (evaluation.getVisitDate() != null ? evaluation.getVisitDate() : "-")));
            document.add(new Paragraph("Visit Mode: " +
                    (evaluation.getVisitMode() != null ? evaluation.getVisitMode() : "-")));

            document.add(new Paragraph("\nPart A: Visit Details").setBold());
            document.add(new Paragraph("Discussion Summary: " +
                    (evaluation.getDiscussionSummary() != null ? evaluation.getDiscussionSummary() : "-")));
            document.add(new Paragraph("Overall Observation: " +
                    (evaluation.getOverallObservation() != null ? evaluation.getOverallObservation() : "-")));

            document.add(new Paragraph("\nPart B1: Student Reflection (Comments)").setBold());
            document.add(new Paragraph("Role Understanding Comment: " +
                    (evaluation.getRoleUnderstandingComment() != null ? evaluation.getRoleUnderstandingComment() : "-")));
            document.add(new Paragraph("Learning Understanding Comment: " +
                    (evaluation.getLearningUnderstandingComment() != null ? evaluation.getLearningUnderstandingComment() : "-")));

            document.add(new Paragraph("\nPart B2: Likert Scores (1-5)").setBold());
            document.add(new Paragraph("Reflection Likert: " + safe(evaluation.getReflectionLikert())));
            document.add(new Paragraph("Engagement Likert: " + safe(evaluation.getEngagementLikert())));
            document.add(new Paragraph("Placement Suitability Likert: " + safe(evaluation.getPlacementSuitabilityLikert())));
            document.add(new Paragraph("Logbook Likert: " + safe(evaluation.getLogbookLikert())));
            document.add(new Paragraph("Lecturer Overall Likert: " + safe(evaluation.getLecturerOverallLikert())));

            document.add(new Paragraph("\nTotal Score: " +
                    (evaluation.getTotalScore40() != null ? evaluation.getTotalScore40() : 0) + " / 40").setBold());

            document.add(new Paragraph("\nAdditional Remarks: " +
                    (evaluation.getAdditionalRemarks() != null ? evaluation.getAdditionalRemarks() : "-")));

            // Optional: schedule info
            if (schedule != null) {
                document.add(new Paragraph("\n(From Visit Schedule)").setItalic());
                document.add(new Paragraph("Scheduled Date: " + (schedule.getVisitDate() != null ? schedule.getVisitDate() : "-")));
            }

            document.close();
        }
    }

    private String safe(Integer v) {
        return v == null ? "-" : String.valueOf(v);
    }
}