package com.example.itsystem.controller;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitEvaluation;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.VisitEvaluationRepository;
import com.example.itsystem.service.VisitScheduleService;

// ==== 新增 import ====
import com.example.itsystem.service.StudentAssessmentService;
import com.example.itsystem.model.StudentAssessment;
import java.math.BigDecimal;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Objects;  // 你之前少的这个也要有
import java.math.BigDecimal;

@Controller
@RequestMapping("/lecturer/evaluation")
public class EvaluationController {

    @Autowired
    private VisitEvaluationRepository evaluationRepository;

    @Autowired
    private VisitScheduleService visitScheduleService;

    @Autowired
    private UserRepository userRepository;

    // ==== 新增：注入成绩汇总服务 ====
    @Autowired
    private StudentAssessmentService studentAssessmentService;

    // ==== 学期工具（如果你有动态学期，可替换为读取配置/数据库）====
    private String currentSession() {
        return "2024/2025-2";
    }

    private static Integer toInt(BigDecimal v) { return v == null ? null : v.intValue(); }


    @GetMapping("/form/{visitId}")
    public String showEvaluationForm(@PathVariable Long visitId,
                                     @RequestParam(name="session", required=false) String sessionFilter,
                                     HttpSession session, Model model) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) return "redirect:/login";

        VisitSchedule visit = visitScheduleService.findById(visitId);
        if (visit == null || !Objects.equals(visit.getLecturerId(), lecturer.getId()))
            return "redirect:/lecturer/evaluation/list";

        String finalSession = (sessionFilter != null && !sessionFilter.isBlank())
                ? sessionFilter : currentSession();

        // 取/建 evaluation（A/B部分）
        List<VisitEvaluation> list = evaluationRepository.findByVisitId(visitId);
        VisitEvaluation evaluation = list.isEmpty() ? new VisitEvaluation() : list.get(0);

        evaluation.setVisitId(visit.getId());
        evaluation.setStudentId(visit.getStudentId());
        evaluation.setLecturerId(lecturer.getId());

        // ✅ session 必须写进去并且回传表单（你前端已加 hidden th:field="*{session}"）
        evaluation.setSession(finalSession);

        // ✅ 回填 C 部分：按 student+session+lecturerId 精准读
        StudentAssessment sa = studentAssessmentService.getOrCreate(
                visit.getStudentId(), finalSession, lecturer.getId());

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
                                   @RequestParam(name="session", required=false) String sessionFilter,
                                   HttpSession session,
                                   RedirectAttributes ra) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        // ✅ 先保存 Part A / Part B 文本（visit_evaluation 表）
        evaluationRepository.save(evaluation);

        // ✅ session：空字符串也要 fallback，否则会写进 session=""
        String sessionStr = (evaluation.getSession() == null || evaluation.getSession().isBlank())
                ? currentSession()
                : evaluation.getSession();

        Long studentId = evaluation.getStudentId();

        int e10 = (evaluation.getVlEvaluation10() == null) ? 0 : evaluation.getVlEvaluation10();
        int a5  = (evaluation.getVlAttendance5() == null) ? 0 : evaluation.getVlAttendance5();
        int l5  = (evaluation.getVlLogbook5() == null) ? 0 : evaluation.getVlLogbook5();
        int r40 = (evaluation.getVlFinalReport40() == null) ? 0 : evaluation.getVlFinalReport40();

        // ✅ 保存 Part C 到 student_assessment（按 student+session+lecturerId）
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


    @GetMapping("/list")
    public String showEvaluationList(
            @RequestParam(name = "session", required = false) String sessionFilter,
            Model model,
            HttpSession session) {

        // 1) 登录/角色检查
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        // 2) 下拉选项：该讲师名下所有学生的去重 Session
        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        // 3) 取该讲师的所有 visit（不再限制 Confirmed）
        List<VisitSchedule> allVisits = visitScheduleService.findByLecturerId(lecturer.getId());

        // 4) 如果选择了 session，则只保留该 session 下的学生的 visit
        List<VisitSchedule> filtered = allVisits;
        if (sessionFilter != null && !sessionFilter.isBlank()) {
            List<User> studentsInSession = userRepository.findByLecturerAndSession(lecturer, sessionFilter);
            var allowedIds = studentsInSession.stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());

            filtered = allVisits.stream()
                    .filter(v -> allowedIds.contains(v.getStudentId()))
                    .collect(Collectors.toList());
        }

        // 5) 同一个学生只保留“最新一条 visit”
        //    这里假设 id 自增，id 越大越新
        Map<Long, VisitSchedule> latestByStudent = new HashMap<>();
        for (VisitSchedule v : filtered) {
            Long sid = v.getStudentId();
            VisitSchedule existing = latestByStudent.get(sid);
            if (existing == null || v.getId() > existing.getId()) {
                latestByStudent.put(sid, v);
            }
        }

        // 6) 构建学生姓名映射 & 转成列表
        Map<Long, String> studentNameMap = new HashMap<>();
        for (VisitSchedule v : latestByStudent.values()) {
            Long sid = v.getStudentId();
            if (!studentNameMap.containsKey(sid)) {
                userRepository.findById(sid).ifPresent(u -> studentNameMap.put(sid, u.getName()));
            }
        }

        List<VisitSchedule> visitList = latestByStudent.values().stream().toList();

        model.addAttribute("visitList", visitList);
        model.addAttribute("studentNameMap", studentNameMap);
        return "evaluation"; // 对应 templates/evaluation.html
    }



    @GetMapping("/pdf/{visitId}")
    public void downloadEvaluationPdf(@PathVariable Long visitId, HttpServletResponse response) throws IOException {
        List<VisitEvaluation> evalList = evaluationRepository.findByVisitId(visitId);
        if (evalList.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Evaluation not found");
            return;
        }

        VisitEvaluation evaluation = evalList.get(0);
        VisitSchedule schedule = visitScheduleService.findById(visitId); // 获取 VisitSchedule

        // 设置响应头
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=evaluation_" + visitId + ".pdf");

        // PDF 生成
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