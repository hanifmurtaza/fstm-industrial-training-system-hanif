package com.example.itsystem.controller;

import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitEvaluation;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.VisitEvaluationRepository;
import com.example.itsystem.service.StudentAssessmentService;
import com.example.itsystem.service.VisitScheduleService;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    // If you have dynamic session, replace this method later
    private String currentSession() {
        return "2024/2025-2";
    }

    private static Integer toInt(BigDecimal v) {
        return v == null ? null : v.intValue();
    }

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

        User student = userRepository.findById(visit.getStudentId()).orElse(null);

        String finalSession = (sessionFilter != null && !sessionFilter.isBlank())
                ? sessionFilter.trim()
                : (student != null && student.getSession() != null && !student.getSession().isBlank()
                ? student.getSession().trim()
                : "2024/2025-2"); // fallback only

        // Get/create evaluation (Part A/B)
        List<VisitEvaluation> list = evaluationRepository.findByVisitId(visitId);
        VisitEvaluation evaluation = list.isEmpty() ? new VisitEvaluation() : list.get(0);

        evaluation.setVisitId(visit.getId());
        evaluation.setStudentId(visit.getStudentId());
        evaluation.setLecturerId(lecturer.getId());

        // session must be set (and front-end posts hidden field)
        evaluation.setSession(finalSession);

        // Fill Part C from student_assessment (student + session + lecturerId)
        StudentAssessment sa = studentAssessmentService.getOrCreate(
                visit.getStudentId(), finalSession, lecturer.getId()
        );

        evaluation.setVlEvaluation10(toInt(sa.getVlEvaluation10()));
        evaluation.setVlAttendance5(toInt(sa.getVlAttendance5()));
        evaluation.setVlLogbook5(toInt(sa.getVlLogbook5()));
        evaluation.setVlFinalReport40(toInt(sa.getVlFinalReport40()));

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

        // Save Part A/B (visit_evaluation table)
        evaluationRepository.save(evaluation);

        // session fallback (avoid saving empty session)
        User student = (evaluation.getStudentId() != null)
                ? userRepository.findById(evaluation.getStudentId()).orElse(null)
                : null;

        String sessionStr = (evaluation.getSession() != null && !evaluation.getSession().isBlank())
                ? evaluation.getSession().trim()
                : (student != null && student.getSession() != null && !student.getSession().isBlank()
                ? student.getSession().trim()
                : "2024/2025-2");


        Long studentId = evaluation.getStudentId();

        int e10 = (evaluation.getVlEvaluation10() == null) ? 0 : evaluation.getVlEvaluation10();
        int a5  = (evaluation.getVlAttendance5() == null) ? 0 : evaluation.getVlAttendance5();
        int l5  = (evaluation.getVlLogbook5() == null) ? 0 : evaluation.getVlLogbook5();
        int r40 = (evaluation.getVlFinalReport40() == null) ? 0 : evaluation.getVlFinalReport40();

        // Save Part C to student_assessment (student + session + lecturerId)
        studentAssessmentService.saveVisitingLecturerScores(
                studentId, sessionStr, lecturer.getId(),
                BigDecimal.valueOf(e10),
                BigDecimal.valueOf(a5),
                BigDecimal.valueOf(l5),
                BigDecimal.valueOf(r40),
                true
        );

        ra.addFlashAttribute("successMessage", "Evaluation submitted successfully.");

        if (sessionFilter != null && !sessionFilter.isBlank()) {
            ra.addAttribute("session", sessionFilter);
        }
        return "redirect:/lecturer/evaluation/list";
    }

    /**
     * ✅ Merged improvement:
     * List page based on lecturer-bound students (even if no visit yet),
     * while still providing latest visit for each student if available.
     */
    @GetMapping("/list")
    public String showEvaluationList(@RequestParam(name = "session", required = false) String sessionFilter,
                                     Model model,
                                     HttpSession session) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) return "redirect:/login";

        // Dropdown options: distinct sessions under this lecturer
        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        // ✅ Main list: students bound to lecturer (optionally filtered by session)
        List<User> students = (sessionFilter != null && !sessionFilter.isBlank())
                ? userRepository.findByLecturerAndSession(lecturer, sessionFilter)
                : userRepository.findByLecturer(lecturer);

        // Get all visits for lecturer to find latest per student
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

        List<VisitEvaluation> evalList = evaluationRepository.findByVisitId(visitId);
        if (evalList.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Evaluation not found");
            return;
        }

        VisitEvaluation evaluation = evalList.get(0);
        VisitSchedule schedule = visitScheduleService.findById(visitId);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=evaluation_" + visitId + ".pdf");

        try (OutputStream os = response.getOutputStream()) {
            PdfWriter writer = new PdfWriter(os);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Student Evaluation Form").setBold().setFontSize(16));

            document.add(new Paragraph("Student ID: " + evaluation.getStudentId()));
            document.add(new Paragraph("Date of Visit: " +
                    (schedule != null && schedule.getVisitDate() != null ? schedule.getVisitDate().toString() : "-")));
            document.add(new Paragraph("Summary of Discussion: " +
                    (evaluation.getSummary() != null ? evaluation.getSummary() : "-")));

            document.add(new Paragraph("Interview Outcome: " +
                    (evaluation.getInterviewOutcome() != null ? evaluation.getInterviewOutcome() : "-")));
            document.add(new Paragraph("Logbook Review: " +
                    (evaluation.getLogbookReview() != null ? evaluation.getLogbookReview() : "-")));
            document.add(new Paragraph("Workplace Suitability: " +
                    (evaluation.getWorkplaceSuitability() != null ? evaluation.getWorkplaceSuitability() : "-")));
            document.add(new Paragraph("Career Potential: " +
                    (evaluation.getCareerPotential() != null ? evaluation.getCareerPotential() : "-")));

            document.close();
        }
    }
}
