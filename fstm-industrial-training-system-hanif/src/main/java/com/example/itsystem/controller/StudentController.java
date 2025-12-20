package com.example.itsystem.controller;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
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
import com.example.itsystem.model.Company;
import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
import com.example.itsystem.repository.CompanyRepository;
import com.example.itsystem.repository.PlacementRepository;

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
    @Autowired private PlacementRepository placementRepository;
    @Autowired private CompanyRepository companyRepository;


    // ★ 访问评分表
    @Autowired private StudentAssessmentRepository studentAssessmentRepository;

    // ★ 学生公司信息表
    @Autowired private CompanyInfoRepository companyInfoRepository;

    // ★ 当前学期（优先取用户资料里的 session）
    private String currentSession(User user) {
        if (user != null && user.getSession() != null && !user.getSession().isBlank()) {
            return user.getSession();
        }
        return "2024/2025-2";
    }

    // ==========================
    //         Dashboard
    // ==========================
    @GetMapping("/student-dashboard")
    public String studentDashboard(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        model.addAttribute("name", user.getName());

        // 来访计划
        VisitSchedule visit = visitScheduleService.findUpcomingForStudent(user.getId());
        model.addAttribute("visitSchedule", visit);

        // ===== 为避免学期不一致拿不到成绩，按优先级尝试 =====
        String byVisit = null; // 未来 VisitSchedule 有 session/term 字段再接入
        String byUser = (user.getSession() != null && !user.getSession().isBlank()) ? user.getSession() : null;
        String fallbackDefault = "2024/2025-2";

        ArrayList<String> candidates = new ArrayList<>();
        if (byVisit != null && !byVisit.isBlank()) candidates.add(byVisit.trim());
        if (byUser != null && !byUser.isBlank()) candidates.add(byUser.trim());
        if (fallbackDefault != null) candidates.add(fallbackDefault.trim());
        candidates = new ArrayList<>(new LinkedHashSet<>(candidates)); // 去重保序

        StudentAssessment sa = null;
        for (String s : candidates) {
            sa = studentAssessmentRepository.findByStudentUserIdAndSession(user.getId(), s).orElse(null);
            if (sa != null) break;
        }
        if (sa == null) {
            sa = studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(user.getId()).orElse(null);
        }
        // ===============================================

        // 分数（null → 0 用于进度条；是否已评分仍用 null 判断）
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
                sa.getVlEvaluation10() != null ||
                        sa.getVlAttendance5() != null ||
                        sa.getVlLogbook5() != null ||
                        sa.getVlFinalReport40() != null
        );
        boolean hasIS = sa != null && (
                sa.getIsSkills20() != null ||
                        sa.getIsCommunication10() != null ||
                        sa.getIsTeamwork10() != null
        );
        boolean hasLogbook = sa != null && sa.getVlLogbook5() != null;
        boolean hasFinal   = sa != null && sa.getVlFinalReport40() != null;

        Integer vlShown  = hasVL ? (vlEval10 + vlAttend5 + vlLog5 + vlRep40) : null; // /60
        Integer isShown  = hasIS ? (isSkill20 + isComm10 + isTeam10) : null;          // /40
        Integer logShown = hasLogbook ? vlLog5 : null;                                // /5
        Integer frShown  = hasFinal ? vlRep40 : null;                                 // /40

        Map<String, Integer> evaluationStatus = new LinkedHashMap<>();
        evaluationStatus.put("FinalReport", frShown);
        evaluationStatus.put("IndustrySupervisor", isShown);
        evaluationStatus.put("Logbook", logShown);
        evaluationStatus.put("VisitingLecturer", vlShown);
        model.addAttribute("evaluationStatus", evaluationStatus);

        Map<String, Integer> maxMap = new HashMap<>();
        maxMap.put("FinalReport", 40);
        maxMap.put("IndustrySupervisor", 40);
        maxMap.put("Logbook", 5);
        maxMap.put("VisitingLecturer", 60);
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

    // ==========================
    //      Student Company Info
    // ==========================
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

        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        if (latest != null && latest.getStatus() != CompanyInfoStatus.REJECTED) {
            ra.addFlashAttribute("error",
                    "You already submitted your company information. Current status: " + latest.getStatus() +
                            ". Please wait for admin to process it.");
            return "redirect:/student/company-info";
        }

        CompanyInfo info = new CompanyInfo();
        info.setStudentId(student.getId());
        info.setCompanyName(companyName.trim());
        info.setAddress(companyAddress);
        info.setSupervisorName(supervisorName.trim());
        info.setSupervisorEmail(supervisorEmail.trim());
        info.setSupervisorPhone(supervisorPhone);
        info.setStatus(CompanyInfoStatus.PENDING);

        companyInfoRepository.save(info);

        ra.addFlashAttribute("success",
                "Your company information has been submitted and is now pending approval.");
        return "redirect:/student/company-info";
    }

    // ==========================
    //          Profile
    // ==========================
    @GetMapping("/student/profile")
    public String viewProfile(Model model, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        // Reload from DB so updates reflect immediately
        User fresh = userRepository.findById(student.getId()).orElse(student);
        session.setAttribute("user", fresh);

        // ✅ Company should come from Placement (APPROVED) -> Company master
        String companyName = "-";
        Placement approved = placementRepository
                .findTopByStudentIdAndStatusOrderByIdDesc(fresh.getId(), PlacementStatus.APPROVED)
                .orElse(null);

        if (approved != null && approved.getCompanyId() != null) {
            companyName = companyRepository.findById(approved.getCompanyId())
                    .map(Company::getName)
                    .orElse("-");
        }

        List<User> lecturers = userRepository.findByRole("teacher");

        model.addAttribute("user", fresh);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("companyName", companyName); // ✅ send to view
        return "student/student-profile";
    }

    // choose lecturer (from second file)
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

    // update profile fields (fixed redirect to existing route)
    @PostMapping("/student/profile/update")
    public String updateProfile(@RequestParam String session,
                                HttpSession httpSession) {
        User user = (User) httpSession.getAttribute("user");
        if (user != null) {
            user.setSession(session);
            userRepository.save(user);

            User updated = userRepository.findById(user.getId()).orElse(user);
            httpSession.setAttribute("user", updated);
        }
        return "redirect:/student/profile?success=true";
    }


    // ==========================
    //          Visit
    // ==========================
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

    // ==========================
    //          Logbook
    // ==========================
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

    // ==========================
    //          Logout
    // ==========================
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
