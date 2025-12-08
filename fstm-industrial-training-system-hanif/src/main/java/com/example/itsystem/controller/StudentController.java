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
import java.util.ArrayList;
import java.util.LinkedHashSet;

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
import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.model.CompanyInfoStatus;

import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.StudentAssessmentRepository;
import com.example.itsystem.repository.CompanyInfoRepository;

import com.example.itsystem.service.LogbookEntryService;
import com.example.itsystem.service.VisitScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StudentController {

    @Autowired private UserRepository userRepository;
    @Autowired private VisitScheduleService visitScheduleService;
    @Autowired private LogbookEntryRepository logbookEntryRepository;
    @Autowired private LogbookEntryService logbookEntryService;

    // ★ 新增：访问评分表
    @Autowired private StudentAssessmentRepository studentAssessmentRepository;

    // ★ 新增：学生公司信息表
    @Autowired private CompanyInfoRepository companyInfoRepository;

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
        String byVisit = null;

        // ① 用户档案里的学期
        String byUser  = (user.getSession() != null && !user.getSession().isBlank())
                ? user.getSession()
                : null;

        // ② 系统默认学期
        String fallbackDefault = "2024/2025-2";

        ArrayList<String> candidates = new ArrayList<>();
        if (byVisit != null && !byVisit.isBlank()) candidates.add(byVisit.trim());
        if (byUser  != null && !byUser.isBlank())  candidates.add(byUser.trim());
        if (fallbackDefault != null)               candidates.add(fallbackDefault.trim());
        candidates = new ArrayList<>(new LinkedHashSet<>(candidates)); // 去重保序

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

        // 取各项分数（null → 0）
        int vlEval10  = sa != null && sa.getVlEvaluation10()  != null ? sa.getVlEvaluation10().intValue()  : 0;
        int vlAttend5 = sa != null && sa.getVlAttendance5()   != null ? sa.getVlAttendance5().intValue()   : 0;
        int vlLog5    = sa != null && sa.getVlLogbook5()      != null ? sa.getVlLogbook5().intValue()      : 0;
        int vlRep40   = sa != null && sa.getVlFinalReport40() != null ? sa.getVlFinalReport40().intValue() : 0;

        int isSkill20 = sa != null && sa.getIsSkills20()        != null ? sa.getIsSkills20().intValue()        : 0;
        int isComm10  = sa != null && sa.getIsCommunication10() != null ? sa.getIsCommunication10().intValue() : 0;
        int isTeam10  = sa != null && sa.getIsTeamwork10()      != null ? sa.getIsTeamwork10().intValue()      : 0;

        Integer total100 = sa != null && sa.getTotal100() != null ? sa.getTotal100().intValue() : null;
        String grade = sa != null ? sa.getGrade() : null;

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

    // ======================================================
    //      NEW: Student Company Info (one active submission)
    // ======================================================

    @GetMapping("/student/company-info")
    public String companyInfoPage(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) {
            return "redirect:/login";
        }

        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        boolean canSubmit = (latest == null || latest.getStatus() == CompanyInfoStatus.REJECTED);

        model.addAttribute("student", student);
        model.addAttribute("latestInfo", latest);
        model.addAttribute("canSubmit", canSubmit);

        // <-- IMPORTANT: this must match your template name
        return "student/company-form";
    }


    @PostMapping("/student/company-info")
    public String submitCompanyInfo(@RequestParam String companyName,
                                    @RequestParam(required = false) String companyAddress,
                                    @RequestParam String supervisorName,
                                    @RequestParam String supervisorEmail,
                                    @RequestParam(required = false) String supervisorPhone,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) {
            return "redirect:/login";
        }

        // latest submission (if any)
        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        // Block if there is an existing NON-rejected submission
        if (latest != null && latest.getStatus() != CompanyInfoStatus.REJECTED) {
            ra.addFlashAttribute("error",
                    "You already submitted your company information. " +
                            "Current status: " + latest.getStatus() +
                            ". Please wait for admin to process it.");
            return "redirect:/student/company-info";
        }

        // Either first time, or last one was REJECTED → allow new PENDING submission
        CompanyInfo info = new CompanyInfo();
        info.setStudentId(student.getId());
        info.setCompanyName(companyName.trim());
        info.setAddress(companyAddress);
        info.setSupervisorName(supervisorName.trim());
        info.setSupervisorEmail(supervisorEmail.trim());
        info.setSupervisorPhone(supervisorPhone);
        info.setStatus(CompanyInfoStatus.PENDING);
        // if your entity has submittedAt or session fields, you can set them here

        companyInfoRepository.save(info);

        ra.addFlashAttribute("success",
                "Your company information has been submitted and is now pending approval.");
        return "redirect:/student/company-info";
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
        response.setHeader("Content-Disposition",
                "attachment; filename=logbook_" + entry.getWeekStartDate() + ".pdf");

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
