package com.example.itsystem.controller;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.element.Text;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.model.StudentAssessment;

import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.StudentAssessmentRepository;

import com.example.itsystem.service.LogbookEntryService;
import com.example.itsystem.service.VisitScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.LinkedHashSet;


@Controller
public class StudentController {

    @Autowired private UserRepository userRepository;
    @Autowired private VisitScheduleService visitScheduleService;
    @Autowired private LogbookEntryRepository logbookEntryRepository;
    @Autowired private LogbookEntryService logbookEntryService;

    // ★ 新增：访问评分表
    @Autowired private StudentAssessmentRepository studentAssessmentRepository;

    // ★ 新增：当前学期（优先取用户资料里的 session）
    private String currentSession(User user) {
        if (user != null && user.getSession() != null && !user.getSession().isBlank()) {
            return user.getSession();
        }
        return "2024/2025-2";
    }

    // ======= 重写后的学生首页 =======
    @GetMapping("/student-dashboard")
    public String studentDashboard(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        model.addAttribute("name", user.getName());

        // 来访计划
        VisitSchedule visit = visitScheduleService.findUpcomingForStudent(user.getId());
        model.addAttribute("visitSchedule", visit);

// ===== 关键差异开始：为避免学期不一致拿不到成绩，按优先级尝试 =====
// VisitSchedule 暂时没有 session 字段，先置为 null（以后有了再用 visit.getSession()/getSemester()/getTerm()）
        String byVisit = null;

// ① 用户档案里的学期
        String byUser  = (user.getSession() != null && !user.getSession().isBlank())
                ? user.getSession()
                : null;

// ② 系统默认学期（按你系统需要调整）
        String fallbackDefault = "2024/2025-2";

// 按优先级去重组织候选学期
        ArrayList<String> candidates = new ArrayList<>();
        if (byVisit != null && !byVisit.isBlank()) candidates.add(byVisit.trim());
        if (byUser  != null && !byUser.isBlank())  candidates.add(byUser.trim());
        if (fallbackDefault != null)               candidates.add(fallbackDefault.trim());
        candidates = new ArrayList<>(new LinkedHashSet<>(candidates)); // 去重保序

// 依次尝试；都没命中时，回退到该学生“最新一条”评分记录
        StudentAssessment sa = null;
        for (String s : candidates) {
            sa = studentAssessmentRepository
                    .findByStudentUserIdAndSession(user.getId(), s)
                    .orElse(null);
            if (sa != null) break;
        }
        if (sa == null) {
            sa = studentAssessmentRepository
                    .findTopByStudentUserIdOrderByIdDesc(user.getId())
                    .orElse(null);
        }
// ===== 关键差异结束 =====


        // 取各项分数（null → 0 用于详细进度条；“是否已评分”仍用 null 判断）
        int vlEval10  = sa != null && sa.getVlEvaluation10()  != null ? sa.getVlEvaluation10().intValue()  : 0;
        int vlAttend5 = sa != null && sa.getVlAttendance5()   != null ? sa.getVlAttendance5().intValue()   : 0;
        int vlLog5    = sa != null && sa.getVlLogbook5()      != null ? sa.getVlLogbook5().intValue()      : 0;
        int vlRep40   = sa != null && sa.getVlFinalReport40() != null ? sa.getVlFinalReport40().intValue() : 0;

        int isSkill20 = sa != null && sa.getIsSkills20()        != null ? sa.getIsSkills20().intValue()        : 0;
        int isComm10  = sa != null && sa.getIsCommunication10() != null ? sa.getIsCommunication10().intValue() : 0;
        int isTeam10  = sa != null && sa.getIsTeamwork10()      != null ? sa.getIsTeamwork10().intValue()      : 0;

        Integer total100 = sa != null && sa.getTotal100() != null ? sa.getTotal100().intValue() : null;
        String grade = sa != null ? sa.getGrade() : null;

        // 是否已评分（0 分也算已评分）
        boolean hasVL = sa != null && (
                sa.getVlEvaluation10()  != null ||
                        sa.getVlAttendance5()   != null ||
                        sa.getVlLogbook5()      != null ||
                        sa.getVlFinalReport40() != null
        );
        boolean hasIS = sa != null && (
                sa.getIsSkills20()        != null ||
                        sa.getIsCommunication10() != null ||
                        sa.getIsTeamwork10()      != null
        );
        boolean hasLogbook = sa != null && sa.getVlLogbook5()      != null;
        boolean hasFinal   = sa != null && sa.getVlFinalReport40() != null;

        // Evaluation Progress 四张卡：已评分显示分数，否则 Pending(null)
        Integer vlShown  = hasVL      ? (vlEval10 + vlAttend5 + vlLog5 + vlRep40) : null; // /60
        Integer isShown  = hasIS      ? (isSkill20 + isComm10 + isTeam10)         : null; // /40
        Integer logShown = hasLogbook ? vlLog5                                    : null; // /5
        Integer frShown  = hasFinal   ? vlRep40                                   : null; // /40

        Map<String, Integer> evaluationStatus = new LinkedHashMap<>();
        evaluationStatus.put("FinalReport",        frShown);
        evaluationStatus.put("IndustrySupervisor", isShown);
        evaluationStatus.put("Logbook",            logShown);
        evaluationStatus.put("VisitingLecturer",   vlShown);
        model.addAttribute("evaluationStatus", evaluationStatus);

        Map<String, Integer> maxMap = new HashMap<>();
        maxMap.put("FinalReport",        40);
        maxMap.put("IndustrySupervisor", 40);
        maxMap.put("Logbook",             5);
        maxMap.put("VisitingLecturer",   60);
        model.addAttribute("maxMap", maxMap);

        // 详细分与总分
        int vlSum = vlEval10 + vlAttend5 + vlLog5 + vlRep40;
        int isSum = isSkill20 + isComm10 + isTeam10;

        model.addAttribute("vlEval10", vlEval10);
        model.addAttribute("vlAttend5", vlAttend5);
        model.addAttribute("vlLog5", vlLog5);
        model.addAttribute("vlRep40", vlRep40);
        model.addAttribute("vlSum", vlSum);

        model.addAttribute("isSkill20", isSkill20);
        model.addAttribute("isComm10", isComm10);
        model.addAttribute("isTeam10", isTeam10);
        model.addAttribute("isSum", isSum);

        model.addAttribute("total100", total100 != null ? total100 : (vlSum + isSum));
        model.addAttribute("grade", grade);

        model.addAttribute("hasVL", hasVL);
        model.addAttribute("hasIS", hasIS);
        model.addAttribute("hasLogbook", hasLogbook);
        model.addAttribute("hasFinal", hasFinal);

        return "student-dashboard";
    }


    // ===== 下面保持你原有的其余映射不变 =====

    @GetMapping("/student/profile")
    public String viewProfile(Model model, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";
        List<User> lecturers = userRepository.findByRole("teacher");
        model.addAttribute("user", student);
        model.addAttribute("lecturers", lecturers);
        return "student/student-profile";
    }

    @PostMapping("/student-profile/update")
    public String updateProfile(@RequestParam String session,
                                @RequestParam String company,
                                HttpSession httpSession) {
        User user = (User) httpSession.getAttribute("user");
        if (user != null) {
            user.setSession(session);
            user.setCompany(company);
            userRepository.save(user);
            httpSession.setAttribute("user", user);
        }
        return "redirect:/student-profile?success=true";
    }

    @PostMapping("/student/visit/confirm")
    public String confirmVisit(@RequestParam Long visitId, HttpSession session) {
        VisitSchedule visit = visitScheduleService.findById(visitId);
        visit.setStatus("Confirmed");
        visitScheduleService.saveSchedule(visit);
        return "redirect:/student-dashboard";
    }

    @PostMapping("/student/visit/reschedule")
    public String rescheduleVisit(@RequestParam Long visitId, HttpSession session) {
        VisitSchedule visit = visitScheduleService.findById(visitId);
        visit.setStatus("RescheduleRequested");
        visitScheduleService.saveSchedule(visit);
        return "redirect:/student-dashboard";
    }

    @GetMapping("/student/logbook/new")
    public String showLogbookForm(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) {
            return "redirect:/login";
        }
        model.addAttribute("logbookEntry", new LogbookEntry());
        return "student/logbook-form";
    }

    @PostMapping("/student/logbook/save")
    public String saveLogbookEntry(@ModelAttribute LogbookEntry logbookEntry,
                                   @RequestParam("photoFile") MultipartFile photoFile,
                                   HttpSession session) throws IOException {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        logbookEntry.setStudentId(student.getId());
        logbookEntry.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        if (!photoFile.isEmpty()) {
            String uploadDir = "uploads/logbook/";
            String filename = UUID.randomUUID() + "_" + photoFile.getOriginalFilename();
            File saveFile = new File(uploadDir, filename);
            saveFile.getParentFile().mkdirs();
            photoFile.transferTo(saveFile);
            logbookEntry.setPhotoPath("/" + uploadDir + filename);
        }

        logbookEntryService.save(logbookEntry);
        return "redirect:/student/logbook/list";
    }

    @GetMapping("/student/logbook/list")
    public String viewLogbookList(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) {
            return "redirect:/login";
        }
        List<LogbookEntry> entries = logbookEntryService.getByStudentId(student.getId());
        model.addAttribute("logbookEntries", entries);
        return "student/logbook-list";
    }

    @GetMapping("/student/logbook")
    public String showLogbookForm(Model model) {
        model.addAttribute("logbookEntry", new LogbookEntry());
        return "logbook-form";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @PostMapping("/student/profile")
    public String updateLecturer(@RequestParam Long lecturerId, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        User lecturer = userRepository.findById(lecturerId).orElse(null);
        if (lecturer != null) {
            student.setLecturer(lecturer);
            userRepository.save(student);
            User updated = userRepository.findById(student.getId()).orElse(student);
            session.setAttribute("user", updated);
        }
        return "redirect:/student/profile";
    }

    @GetMapping("/student/logbook/download/{id}")
    public void downloadSingleLogbookPdf(@PathVariable Long id,
                                         HttpServletResponse response,
                                         HttpSession session) throws IOException {
        User student = (User) session.getAttribute("user");
        if (student == null) {
            response.sendRedirect("/login");
            return;
        }

        LogbookEntry entry = logbookEntryService.getById(id);
        if (entry == null || !entry.getStudentId().equals(student.getId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=logbook_" + entry.getWeekStartDate() + ".pdf");

        PdfWriter writer = new PdfWriter(response.getOutputStream());
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        document.add(new Paragraph("Logbook Entry").setFont(bold).setFontSize(16).setBold().setMarginBottom(15));
        document.add(new Paragraph().add(new Text("Student: ").setFont(bold)).add(new Text(student.getName()).setFont(normal)));
        document.add(new Paragraph().add(new Text("Matric ID: ").setFont(bold)).add(new Text(student.getStudentId()).setFont(normal)));
        document.add(new Paragraph().add(new Text("Week: ").setFont(bold)).add(new Text(entry.getWeekStartDate().toString()).setFont(normal)));
        document.add(new Paragraph("\n"));
        document.add(new Paragraph().add(new Text("Main Task: ").setFont(bold)).add(new Text(entry.getMainTask()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Skills: ").setFont(bold)).add(new Text(entry.getSkills()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Challenges: ").setFont(bold)).add(new Text(entry.getChallenges()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Result: ").setFont(bold)).add(new Text(entry.getResult()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Endorsed: ").setFont(bold)).add(new Text(entry.isEndorsed() ? "Yes" : "No").setFont(normal)));
        document.close();
    }

    @GetMapping("/student/logbook/delete/{id}")
    public String deleteLogbookEntry(@PathVariable Long id, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        LogbookEntry entry = logbookEntryService.getById(id);
        if (entry != null && entry.getStudentId().equals(student.getId())) {
            logbookEntryService.deleteById(id);
        }
        return "redirect:/student/logbook/list";
    }
}
