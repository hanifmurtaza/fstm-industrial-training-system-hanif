package com.example.itsystem.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.example.itsystem.repository.*;
import com.example.itsystem.util.UpmGradeUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.example.itsystem.service.FileStorageService;
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

import com.example.itsystem.service.LogbookEntryService;
import com.example.itsystem.service.VisitScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.itsystem.model.VisitEvaluation;
import java.util.Locale;


@Controller
public class StudentController {

    @Autowired private UserRepository userRepository;
    @Autowired private VisitScheduleService visitScheduleService;

    @Autowired private LogbookEntryRepository logbookEntryRepository;
    @Autowired private LogbookEntryService logbookEntryService;

    @Autowired private PlacementRepository placementRepository;
    @Autowired private CompanyRepository companyRepository;

    @Autowired private StudentAssessmentRepository studentAssessmentRepository;

    @Autowired private CompanyInfoRepository companyInfoRepository;
    @Autowired private VisitEvaluationRepository visitEvaluationRepository;


    @Autowired(required = false)
    private FileStorageService fileStorageService;

    private String currentSession(User user) {
        if (user != null && user.getSession() != null && !user.getSession().isBlank()) {
            return user.getSession();
        }
        return "2024/2025-2";
    }

    //DASHBOARD
    @GetMapping("/student-dashboard")
    public String studentDashboard(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        model.addAttribute("name", user.getName());

        VisitSchedule visit = visitScheduleService.findUpcomingForStudent(user.getId());
        model.addAttribute("visitSchedule", visit);

        // ====== Keep your original StudentAssessment lookup logic ======
        String byUser = (user.getSession() != null && !user.getSession().isBlank()) ? user.getSession() : null;
        String fallbackDefault = "2024/2025-2";

        ArrayList<String> candidates = new ArrayList<>();
        if (byUser != null && !byUser.isBlank()) candidates.add(byUser.trim());
        if (fallbackDefault != null) candidates.add(fallbackDefault.trim());
        candidates = new ArrayList<>(new LinkedHashSet<>(candidates));

        StudentAssessment sa = null;
        for (String s : candidates) {
            sa = studentAssessmentRepository.findByStudentUserIdAndSession(user.getId(), s).orElse(null);
            if (sa != null) break;
        }
        if (sa == null) {
            sa = studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(user.getId()).orElse(null);
        }

        model.addAttribute("sa", sa);


        // ====== Industry Supervisor (40) OFFICIAL: Attributes30 + Overall10 ======
        int isAttr30 = sa != null && sa.getIsAttributes30() != null ? sa.getIsAttributes30().intValue() : 0;
        int isOverall10 = sa != null && sa.getIsOverall10() != null ? sa.getIsOverall10().intValue() : 0;

        // Legacy fallback if official fields are empty (older data)
        if (isAttr30 == 0 && isOverall10 == 0 && sa != null) {
            int legacy = 0;
            if (sa.getIsSkills20() != null) legacy += sa.getIsSkills20().intValue();
            if (sa.getIsCommunication10() != null) legacy += sa.getIsCommunication10().intValue();
            if (sa.getIsTeamwork10() != null) legacy += sa.getIsTeamwork10().intValue();

            // best-effort split for display
            isAttr30 = Math.min(30, legacy);
            isOverall10 = Math.max(0, legacy - isAttr30);
        }

        int isSum40 = isAttr30 + isOverall10;
        boolean hasIS = (sa != null) && (
                sa.getIsAttributes30() != null || sa.getIsOverall10() != null ||
                        sa.getIsSkills20() != null || sa.getIsCommunication10() != null || sa.getIsTeamwork10() != null
        );

        model.addAttribute("isAttr30", isAttr30);
        model.addAttribute("isOverall10", isOverall10);
        model.addAttribute("isSum", isSum40);
        model.addAttribute("hasIS", hasIS);

        // ====== Visiting Lecturer (40) from visit_evaluation ======
        VisitEvaluation ve = visitEvaluationRepository
                .findFirstByStudentIdOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        boolean hasVL = (ve != null);

        int vlRef10 = 0, vlEng10 = 0, vlSuit5 = 0, vlLog5 = 0, vlOverall10 = 0;
        int vlSum40 = 0;

        if (hasVL) {
            vlRef10 = safeLikert(ve.getReflectionLikert()) * 2;
            vlEng10 = safeLikert(ve.getEngagementLikert()) * 2;
            vlSuit5 = safeLikert(ve.getPlacementSuitabilityLikert());
            vlLog5 = safeLikert(ve.getLogbookLikert());
            vlOverall10 = safeLikert(ve.getLecturerOverallLikert()) * 2;

            vlSum40 = (ve.getTotalScore40() != null)
                    ? ve.getTotalScore40()
                    : (vlRef10 + vlEng10 + vlSuit5 + vlLog5 + vlOverall10);
        }

        model.addAttribute("hasVL", hasVL);
        model.addAttribute("vlRef10", vlRef10);
        model.addAttribute("vlEng10", vlEng10);
        model.addAttribute("vlSuit5", vlSuit5);
        model.addAttribute("vlLog5", vlLog5);
        model.addAttribute("vlOverall10", vlOverall10);
        model.addAttribute("vlSum", vlSum40);

        // ============================================================
        // Admin/Coordinator (OFFICIAL 2026)
        // Final Report (10): Written5 + Video5
        // Logbook (10): adminLogbook10
        // ============================================================

        boolean hasReport = sa != null && (sa.getAdminReportWritten5() != null || sa.getAdminReportVideo5() != null);
        int report10 = hasReport
                ? ((sa.getAdminReportWritten5() != null ? sa.getAdminReportWritten5().intValue() : 0)
                + (sa.getAdminReportVideo5() != null ? sa.getAdminReportVideo5().intValue() : 0))
                : 0;

        boolean hasLogbook = sa != null && sa.getAdminLogbook10() != null;
        int logbook10 = hasLogbook ? sa.getAdminLogbook10().intValue() : 0;

        // keep these for HTML detailed section (if needed)
        model.addAttribute("hasReport", hasReport);
        model.addAttribute("report10", report10);
        model.addAttribute("hasLogbook", hasLogbook);
        model.addAttribute("logbook10", logbook10);

        // ====== Evaluation Status cards (UPDATED) ======
        // Keep your keys so student-dashboard.html doesn't break
        Integer frShown  = hasReport ? report10 : null;    // /10
        Integer isShown  = hasIS ? isSum40 : null;         // /40
        Integer lbShown  = hasLogbook ? logbook10 : null;  // /10
        Integer vlShown  = hasVL ? vlSum40 : null;         // /40

        Map<String, Integer> evaluationStatus = new LinkedHashMap<>();
        evaluationStatus.put("FinalReport", frShown);
        evaluationStatus.put("IndustrySupervisor", isShown);
        evaluationStatus.put("Logbook", lbShown);
        evaluationStatus.put("VisitingLecturer", vlShown);
        model.addAttribute("evaluationStatus", evaluationStatus);

        Map<String, Integer> maxMap = new HashMap<>();
        maxMap.put("FinalReport", 10);          // ✅ was 40
        maxMap.put("IndustrySupervisor", 40);
        maxMap.put("Logbook", 10);             // ✅ was 5
        maxMap.put("VisitingLecturer", 40);
        model.addAttribute("maxMap", maxMap);


        model.addAttribute("hasFinal", hasReport);
        model.addAttribute("hasLogbookOld", hasLogbook);

        // ====== Total + Grade (OFFICIAL 40+40+10+10) ======
        int total100 = (hasVL ? vlSum40 : 0)
                + (hasIS ? isSum40 : 0)
                + (hasReport ? report10 : 0)
                + (hasLogbook ? logbook10 : 0);

        model.addAttribute("total100", total100);

        String grade = UpmGradeUtil.gradeFromTotal((double) total100);
        model.addAttribute("grade", grade);


        return "student-dashboard";
    }

    // 放在 StudentController 类里任意位置即可（private helper）
    private int safeLikert(Integer v) {
        if (v == null) return 0;
        if (v < 1) return 0;
        return Math.min(v, 5);
    }


    // ==========================
    //      Student Company Info
    // ==========================
    @GetMapping("/student/company-info")
    public String companyInfoPage(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        // ✅ placement-based lock (source of truth)
        Placement latestPlacement = placementRepository
                .findTopByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        boolean hasActivePlacement = latestPlacement != null &&
                (latestPlacement.getStatus() == PlacementStatus.AWAITING_SUPERVISOR
                        || latestPlacement.getStatus() == PlacementStatus.AWAITING_ADMIN
                        || latestPlacement.getStatus() == PlacementStatus.APPROVED);

        // ✅ final canSubmit rule:
        // - if active placement: cannot submit
        // - else if latest company info is pending: cannot submit
        // - else: can submit (null / rejected / verified)
        boolean canSubmit = !hasActivePlacement &&
                (latest == null || latest.getStatus() != CompanyInfoStatus.PENDING);

        model.addAttribute("student", student);
        model.addAttribute("latestInfo", latest);
        model.addAttribute("canSubmit", canSubmit);

        // ✅ for UI messaging
        model.addAttribute("hasActivePlacement", hasActivePlacement);
        model.addAttribute("latestPlacement", latestPlacement);

        return "student/company-form";
    }


    /**
     * ✅ 提交公司信息 + 实习起止日期校验（>=24周）
     */
    @PostMapping("/student/company-info")
    public String submitCompanyInfo(@RequestParam String companyName,
                                    @RequestParam(required = false) String companyAddress,
                                    @RequestParam String supervisorName,
                                    @RequestParam String supervisorEmail,
                                    @RequestParam(required = false) String supervisorPhone,
                                    @RequestParam("offerLetter") MultipartFile offerLetter,
                                    @RequestParam("internshipStartDate") LocalDate internshipStartDate,
                                    @RequestParam("internshipEndDate") LocalDate internshipEndDate,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        // ✅ block submission if student already has an active placement
        Placement latestPlacement = placementRepository
                .findTopByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        boolean hasActivePlacement = latestPlacement != null &&
                (latestPlacement.getStatus() == PlacementStatus.AWAITING_SUPERVISOR
                        || latestPlacement.getStatus() == PlacementStatus.AWAITING_ADMIN
                        || latestPlacement.getStatus() == PlacementStatus.APPROVED);

        if (hasActivePlacement) {
            ra.addFlashAttribute("error",
                    "You already have an active placement (" + latestPlacement.getStatus()
                            + "). You cannot submit a new company information unless the placement is cancelled.");
            return "redirect:/student/company-info";
        }

        // 1) 不能重复提交（除非 REJECTED）
        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        if (latest != null && latest.getStatus() != CompanyInfoStatus.REJECTED) {
            ra.addFlashAttribute("error",
                    "You already submitted your company information. Current status: " + latest.getStatus()
                            + ". Please wait for admin to process it.");
            return "redirect:/student/company-info";
        }

        // 2) 日期基础校验
        if (internshipStartDate == null || internshipEndDate == null) {
            ra.addFlashAttribute("error", "Please select internship start and end date.");
            return "redirect:/student/company-info";
        }
        if (internshipEndDate.isBefore(internshipStartDate)) {
            ra.addFlashAttribute("error", "Internship end date must be after start date.");
            return "redirect:/student/company-info";
        }

        // 3) ✅ >=24周校验（按天数>=168天）
        long days = ChronoUnit.DAYS.between(internshipStartDate, internshipEndDate) + 1; // 包含当天
        if (days < 168) {
            ra.addFlashAttribute("error", "Internship duration must be greater than 24 weeks.");
            return "redirect:/student/company-info";
        }

        // 3b) ✅ Offer letter required
        if (offerLetter == null || offerLetter.isEmpty()) {
            ra.addFlashAttribute("error", "Offer letter (PDF) is required.");
            return "redirect:/student/company-info";
        }

        if (fileStorageService == null) {
            ra.addFlashAttribute("error", "File upload service is not available. Please contact admin.");
            return "redirect:/student/company-info";
        }

        // 4) 保存
        CompanyInfo info = new CompanyInfo();
        info.setStudentId(student.getId());
        info.setCompanyName(companyName.trim());
        info.setAddress(companyAddress);
        info.setSupervisorName(supervisorName.trim());
        info.setSupervisorEmail(supervisorEmail.trim());
        info.setSupervisorPhone(supervisorPhone);

        info.setInternshipStartDate(internshipStartDate);
        info.setInternshipEndDate(internshipEndDate);

        info.setSession(currentSession(student));
        info.setStatus(CompanyInfoStatus.PENDING);

        // Save first to get ID, then store offer letter under /uploads
        companyInfoRepository.save(info);

        try {
            String offerPath = fileStorageService.storeCompanyOfferLetterPdf(offerLetter, student.getId(), info.getId());
            info.setOfferLetterPath(offerPath);
            companyInfoRepository.save(info);
        } catch (Exception ex) {
            // Rollback the record if file storage fails to keep data consistent
            companyInfoRepository.delete(info);
            ra.addFlashAttribute("error", "Failed to upload offer letter: " + ex.getMessage());
            return "redirect:/student/company-info";
        }

        ra.addFlashAttribute("success", "Your company information has been submitted and is now pending approval.");
        return "redirect:/student/company-info";
    }

    // ==========================
    //          Profile
    // ==========================
    @GetMapping("/student/profile")
    public String viewProfile(Model model, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        User fresh = userRepository.findById(student.getId()).orElse(student);
        session.setAttribute("user", fresh);

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
        model.addAttribute("companyName", companyName);
        return "student/student-profile";
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
    //          Visit (你现有逻辑保留)
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
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        model.addAttribute("logbookEntry", new LogbookEntry());
        return "student/logbook-form";
    }

    @PostMapping("/student/logbook/save")
    public String saveLogbookEntry(@ModelAttribute LogbookEntry logbookEntry,
                                   @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                   HttpSession session) {

        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        logbookEntry.setStudentId(student.getId());
        logbookEntry.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        LogbookEntry saved = logbookEntryRepository.save(logbookEntry);

        if (photoFile != null && !photoFile.isEmpty() && fileStorageService != null) {
            String url = fileStorageService.storeLogbookFile(photoFile, student.getId(), saved.getId());
            saved.setPhotoPath(url);
            logbookEntryRepository.save(saved);
        }

        return "redirect:/student/logbook/list";
    }

    @GetMapping("/student/logbook/list")
    public String viewLogbookList(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

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
    //        Final Report
    // ==========================
    @GetMapping("/student/final-report")
    public String finalReportPage(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        User fresh = userRepository.findById(student.getId()).orElse(student);
        session.setAttribute("user", fresh);

        model.addAttribute("user", fresh);
        return "student/final-report-form";
    }

    @PostMapping("/student/final-report/submit")
    public String submitFinalReport(@RequestParam(value = "reportFile", required = false) MultipartFile reportFile,
                                    @RequestParam(value = "videoLink", required = false) String videoLink,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        User dbStudent = userRepository.findById(student.getId()).orElse(student);

        // ✅ 0) video link 必填（保持你原逻辑）
        if (videoLink == null || videoLink.trim().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please paste your video presentation link.");
            return "redirect:/student/final-report";
        }

        // ✅ 1) Word 文件必填（如果你要允许只更新 video link，把这里改成不必填即可）
        if (reportFile == null || reportFile.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please upload your report in Word format (.doc or .docx).");
            return "redirect:/student/final-report";
        }

        // ✅ 2) 校验扩展名：只允许 doc/docx
        String originalName = reportFile.getOriginalFilename();
        String lower = (originalName == null) ? "" : originalName.toLowerCase(Locale.ROOT);

        if (!(lower.endsWith(".doc") || lower.endsWith(".docx"))) {
            ra.addFlashAttribute("errorMessage", "Only Word files (.doc, .docx) are allowed.");
            return "redirect:/student/final-report";
        }

        // ✅ 3) 保存 Word 文件（调用 FileStorageService）
        if (fileStorageService == null) {
            ra.addFlashAttribute("errorMessage", "File storage service is not available. Please contact admin.");
            return "redirect:/student/final-report";
        }

        // 推荐：新增一个 storeFinalReportWord() 方法（下面我也给你代码）
        String wordUrl = fileStorageService.storeFinalReportWord(reportFile, dbStudent.getId());

        // ✅ 4) 存储路径：为了不改数据库字段，先复用 finalReportPdfPath 字段存 Word 链接（最少改动最稳）
        dbStudent.setFinalReportPdfPath(wordUrl);

        // ✅ 5) 保存 video link
        dbStudent.setFinalReportVideoPath(videoLink.trim());

        userRepository.save(dbStudent);
        session.setAttribute("user", dbStudent);

        return "redirect:/student/final-report?success=true";
    }
}